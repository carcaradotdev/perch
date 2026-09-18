/*
 * Copyright 2026 Carcara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.perch

import dev.carcara.perch.serialization.DeepLinkFormat
import dev.carcara.perch.serialization.DeepLinkParameters
import dev.carcara.perch.serialization.percentDecode
import kotlinx.serialization.KSerializer

/**
 * Pattern segment types, mirroring Ktor Server's `RouteSelector` hierarchy:
 * - [Constant] matches a literal path segment
 * - [Parameter] matches and captures a required path parameter, `{name}`
 * - [OptionalParameter] matches and captures an optional path parameter, `{name?}`
 * - [Tailcard] matches the remaining path segments, `{name...}`
 */
internal sealed class PathSegment {
  abstract fun evaluate(segments: List<String>, index: Int): EvaluationResult?

  data class Constant(val value: String) : PathSegment() {
    override fun evaluate(segments: List<String>, index: Int): EvaluationResult? {
      if (index >= segments.size) return null
      if (segments[index] != value) return null
      return EvaluationResult(DeepLinkParameters.Empty, segmentIncrement = 1)
    }
  }

  data class Parameter(val name: String) : PathSegment() {
    override fun evaluate(segments: List<String>, index: Int): EvaluationResult? {
      if (index >= segments.size) return null
      val value = segments[index]
      return EvaluationResult(
        parameters = DeepLinkParameters.build { append(name, value) },
        segmentIncrement = 1,
      )
    }
  }

  data class OptionalParameter(val name: String) : PathSegment() {
    override fun evaluate(segments: List<String>, index: Int): EvaluationResult {
      if (index >= segments.size) {
        return EvaluationResult(DeepLinkParameters.Empty, segmentIncrement = 0)
      }
      val value = segments[index]
      return EvaluationResult(
        parameters = DeepLinkParameters.build { append(name, value) },
        segmentIncrement = 1,
      )
    }
  }

  data class Tailcard(val name: String) : PathSegment() {
    override fun evaluate(segments: List<String>, index: Int): EvaluationResult {
      val remaining = segments.drop(index)
      val params = if (name.isNotEmpty() && remaining.isNotEmpty()) {
        DeepLinkParameters.build {
          remaining.forEach { append(name, it) }
        }
      } else {
        DeepLinkParameters.Empty
      }
      return EvaluationResult(params, segmentIncrement = remaining.size)
    }
  }

  data class EvaluationResult(
    val parameters: DeepLinkParameters,
    val segmentIncrement: Int,
  )

  companion object {
    fun parse(segment: String): PathSegment = when {
      segment.startsWith("{") && segment.endsWith("...}") -> {
        Tailcard(segment.substring(1, segment.length - 4))
      }

      segment.startsWith("{") && segment.endsWith("?}") -> {
        OptionalParameter(segment.substring(1, segment.length - 2))
      }

      segment.startsWith("{") && segment.endsWith("}") -> {
        Parameter(segment.substring(1, segment.length - 1))
      }

      else -> Constant(segment)
    }
  }
}

/** One registered route: its serialiser, its compiled path pattern, and the format to decode with. */
internal class RegisteredRoute<T : Any>(
  private val serializer: KSerializer<T>,
  val routeName: String,
  val pattern: String,
  private val format: DeepLinkFormat,
) {
  private val segments: List<PathSegment> = pattern
    .split("/")
    .filter { it.isNotEmpty() }
    .map { PathSegment.parse(it) }

  private val hasTailcard: Boolean = segments.any { it is PathSegment.Tailcard }

  fun tryParse(urlSegments: List<String>, queryParams: DeepLinkParameters): T? {
    val pathParams = matchPattern(urlSegments) ?: return null

    val allParams = DeepLinkParameters.build {
      appendAll(pathParams)
      appendAll(queryParams)
    }

    return runCatching { format.decodeFromParameters(serializer, allParams) }.getOrNull()
  }

  private fun matchPattern(urlSegments: List<String>): DeepLinkParameters? {
    var urlIndex = 0
    val collectedParams = DeepLinkParameters.build {
      for (segment in segments) {
        val result = segment.evaluate(urlSegments, urlIndex) ?: return null
        appendAll(result.parameters)
        urlIndex += result.segmentIncrement
      }
    }

    // Every URL segment must be consumed, unless a tailcard swallowed the rest.
    if (!hasTailcard && urlIndex != urlSegments.size) {
      return null
    }

    return collectedParams
  }
}

/**
 * Whether a URL has a host is a property of its scheme, never of what the authority happens to
 * contain. In a hierarchical scheme the authority *is* the host by the definition of the URL
 * syntax, single-label ones such as `https://payments/abc` included. A custom app scheme such as
 * `acme://payments/abc` puts the first path element in the authority position by convention and
 * has no host at all.
 *
 * Hardcoded rather than a constructor parameter: two schemes is not a configuration problem, and
 * growing the public constructor for a case nobody has asked for is the worse trade.
 */
internal val HIERARCHICAL_SCHEMES: Set<String> = setOf("http", "https")

/** Scheme, host, path segments, and query of a deep-link URL, with no host-relative guessing. */
internal class UrlLocation private constructor(
  val scheme: String?,
  val host: String?,
  val pathSegments: List<String>,
  val queryParameters: DeepLinkParameters,
) {
  companion object {
    private const val SCHEME_SEPARATOR = "://"
    private val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*$")
    private val schemePrefixPattern = Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*:")

    fun of(rawUrl: String): UrlLocation? {
      val url = rawUrl.trim()
      val separatorIndex = url.indexOf(SCHEME_SEPARATOR)
      val scheme: String?
      val remainder: String
      if (separatorIndex > 0) {
        val candidate = url.substring(0, separatorIndex)
        if (!schemePattern.matches(candidate)) return null
        scheme = candidate.lowercase()
        remainder = url.substring(separatorIndex + SCHEME_SEPARATOR.length)
      } else {
        // A schemeless string is matched as a path and faces neither gate, so anything still
        // carrying a scheme must be rejected rather than demoted to one. `https:/evil.example/x`
        // and `https:\evil.example/x` are hierarchical URLs to a browser but have no "://", and
        // would otherwise arrive as an ordinary first path segment. The character class excludes
        // '/', so an honest path keeps parsing even with a colon in a later segment.
        if (schemePrefixPattern.containsMatchIn(url)) return null
        scheme = null
        remainder = url
      }

      val host: String?
      val afterAuthority: String
      if (scheme != null && scheme in HIERARCHICAL_SCHEMES) {
        // A backslash ends the authority as well. The WHATWG URL standard treats it as a slash
        // for a special scheme, so `https://evil.example\@acme.com/x` names evil.example to a
        // browser. Stopping here is what keeps Perch from reading that same URL as acme.com.
        val authorityEnd = remainder
          .indexOfFirst { it == '/' || it == '?' || it == '#' || it == '\\' }
          .let { if (it < 0) remainder.length else it }
        // An http(s) URL with no authority is malformed. Rejecting it is what stops
        // `https:///payments/abc` from reaching a route without ever facing the host check.
        host = hostOf(remainder.substring(0, authorityEnd)) ?: return null
        afterAuthority = remainder.substring(authorityEnd)
      } else {
        host = null
        afterAuthority = remainder
      }

      // The fragment belongs to neither the path nor the query, and it splits off first because a
      // fragment may itself contain a '?'.
      val beforeFragment = afterAuthority.substringBefore('#')

      val (beforeQuery, queryString) = beforeFragment.split("?", limit = 2)
        .let { it[0] to it.getOrNull(1) }

      // Decoding happens per segment, after the split, so a `%2F` inside a parameter's value
      // becomes a slash in that value rather than a new segment boundary. The authority is never
      // decoded: the host check has to judge the same bytes a browser resolves.
      val pathSegments = beforeQuery.split("/").filter { it.isNotEmpty() }.map(::percentDecode)
      val queryParameters = queryString?.let(::parametersOf) ?: DeepLinkParameters.Empty

      return UrlLocation(scheme, host, pathSegments, queryParameters)
    }

    /** The `a=1&b=2` pairs of a query string. A fragment of a pair with no `=` is discarded. */
    private fun parametersOf(query: String): DeepLinkParameters = DeepLinkParameters.build {
      query.split("&").forEach { param ->
        val pair = param.split("=", limit = 2)
        if (pair.size == 2) append(percentDecode(pair[0]), percentDecode(pair[1]))
      }
    }

    /**
     * The host of an authority, or null when the authority is empty or malformed. Drops the
     * `userinfo@` prefix and the `:port` suffix, neither of which identifies the site, so
     * `https://acme.com:8443/x` matches a configured host of `acme.com` while
     * `https://acme.com@evil.example/x` is judged on `evil.example`.
     */
    private fun hostOf(authority: String): String? {
      if (authority.isEmpty()) return null
      val afterUserInfo = authority.substringAfterLast('@')
      val host = if (afterUserInfo.startsWith("[")) {
        // An IPv6 literal is bracketed, and only a colon after the brackets is a port.
        val closingBracket = afterUserInfo.indexOf(']')
        if (closingBracket < 0) return null
        afterUserInfo.substring(0, closingBracket + 1)
      } else {
        afterUserInfo.substringBefore(':')
      }
      return host.lowercase().ifEmpty { null }
    }
  }
}
