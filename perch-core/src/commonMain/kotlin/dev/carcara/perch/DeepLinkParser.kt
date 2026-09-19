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
import dev.carcara.perch.serialization.encodeToPath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Parses deep-link URLs into route objects.
 *
 * A route is a class annotated [DeepLink] and nothing more: it implements no type of Perch's, so
 * [parse] hands back `Any?` and the caller narrows it.
 *
 * The path pattern decides which of a route's properties come out of the path:
 * - `{param}` required path parameter
 * - `{param?}` optional path parameter
 * - `{param...}` tailcard, matching the remaining segments
 *
 * A URL that carries a scheme resolves only when that scheme is in [schemes], compared
 * case-insensitively. **A URL with no scheme is matched as a path and faces neither the scheme
 * check nor the host check** — `payments/abc123` resolves — so do not read [schemes] as a guarantee
 * about every input. A string carrying a scheme in some other form, such as `https:/evil.example/x`,
 * is rejected rather than treated as a path.
 *
 * Only `http` and `https` have a host: the authority between `://` and the next `/`, `?`, `#` or
 * `\`, with any `userinfo@` prefix and `:port` suffix dropped, and an empty authority rejected. For
 * those two schemes [hosts] must contain the URL's host, which is what keeps a look-alike site from
 * resolving a route the app owns, and the constructor rejects them unless [hosts] is non-empty. An
 * IPv6 host keeps its brackets, so configure it as `setOf("[::1]")`. Every other scheme has no
 * host: `acme://payments/abc` merely puts its first path element where a host would sit.
 *
 * A fragment is discarded and surrounding whitespace trimmed. Path segments and query components
 * are percent-decoded one at a time, after the URL has been split, so `%2F` lands inside a value
 * instead of opening a new segment. The path is not normalised: `.` and `..` reach a `{param...}`
 * tailcard verbatim.
 *
 * ```kotlin
 * @DeepLink("/payments/{id}")
 * class PaymentLink(val id: String)
 *
 * val parser = DeepLinkParser(schemes = setOf("acme", "https"), hosts = setOf("acme.com"))
 * parser.register<PaymentLink>()
 *
 * when (val link = parser.parse("acme://payments/abc123")) {
 *   is PaymentLink -> navigator.push(link)
 *   else -> Unit
 * }
 *
 * parser.toUrl(PaymentLink("abc123")) // "acme://payments/abc123"
 * ```
 *
 * Registration is per instance. Two parsers never share routes.
 */
public class DeepLinkParser(
  schemes: Set<String>,
  hosts: Set<String> = emptySet(),
  private val logger: DeepLinkLogger = DeepLinkLogger.None,
) {

  private val schemes: Set<String> = schemes.map { it.lowercase() }.toSet()
  private val hosts: Set<String> = hosts.map { it.lowercase() }.toSet()

  init {
    // `this.` throughout: inside an initialiser a constructor parameter shadows the property of
    // the same name, and the parameters here are the un-lowercased originals.
    require(this.schemes.isNotEmpty()) { "DeepLinkParser needs at least one scheme" }
    val hierarchical = this.schemes.filter { it in HIERARCHICAL_SCHEMES }
    require(hierarchical.isEmpty() || this.hosts.isNotEmpty()) {
      "DeepLinkParser was given $hierarchical with no hosts, which lets any website on the " +
        "internet deep-link into these routes. Pass the domains you own, for example " +
        "hosts = setOf(\"example.com\"), or drop http and https from schemes."
    }
  }

  /**
   * What [toUrl] puts in front of the encoded path: `"<scheme>:/"` for a custom scheme, or
   * `"https://<host>"` when every configured scheme is hierarchical.
   */
  private val urlPrefix: String = buildUrlPrefix(this.schemes, this.hosts)

  private val format = DeepLinkFormat()

  private val registeredRoutes = mutableListOf<RegisteredRoute<*>>()
  private var warnedEmpty = false

  /** Registers [T] so [parse] can return it. Registering the same route twice is a no-op. */
  public inline fun <reified T : Any> register() {
    register(serializer<T>())
  }

  /** [register] for a serialiser resolved by the caller, rather than reified at the call site. */
  public fun <T : Any> register(serializer: KSerializer<T>) {
    val routeName = serializer.descriptor.serialName
    // A stale generated registration can still name a route whose @DeepLink was removed. Skip it
    // rather than let one bad entry crash app startup.
    val pathPattern = try {
      format.encodeToPathPattern(serializer)
    } catch (error: Exception) {
      logger.error("Skipping deep link route '$routeName' with no @DeepLink path pattern", error)
      return
    }

    for (existing in registeredRoutes) {
      if (existing.routeName == routeName) return
      if (patternsConflict(pathPattern, existing.pattern)) {
        throw DeepLinkCollisionException(
          pattern = pathPattern,
          existingRoute = existing.routeName,
          newRoute = routeName,
        )
      }
    }

    registeredRoutes.add(RegisteredRoute(serializer, routeName, pathPattern, format))
  }

  public fun parse(url: String): Any? {
    warnIfEmpty()
    val location = UrlLocation.of(url) ?: return null
    if (location.scheme != null && location.scheme !in schemes) return null
    if (location.host != null && location.host !in hosts) return null

    for (route in registeredRoutes) {
      val result = route.tryParse(location.pathSegments, location.queryParameters)
      if (result != null) return result
    }
    return null
  }

  /**
   * Reports, once, that this parser has no routes.
   *
   * A parser with no routes answers null to every URL, which is indistinguishable from a URL that
   * matches nothing. The flag races harmlessly: a concurrent first call costs a second message.
   */
  private fun warnIfEmpty() {
    if (registeredRoutes.isNotEmpty() || warnedEmpty) return
    warnedEmpty = true
    logger.error(
      "DeepLinkParser.parse was called with no routes registered, so it can only return null. " +
        "Build the parser with the generated perchParser(), or register routes yourself with " +
        "register<T>().",
      null,
    )
  }

  /**
   * Builds the URL for [deepLink].
   *
   * The scheme is the first custom (non-`http`, non-`https`) entry of `schemes`; when every
   * configured scheme is hierarchical the URL comes out as `https://<first host>/...` instead.
   * Either way the result parses back through [parse]. Path segments and query components are
   * percent-encoded, so a value carrying a slash, a space or a non-ASCII character survives.
   *
   * @throws DeepLinkSerializationException when [deepLink] is not a `@DeepLink` route, or a
   * required placeholder in its path has no value to fill it.
   */
  public inline fun <reified T : Any> toUrl(deepLink: T): String =
    toUrl(serializer<T>(), deepLink)

  /** [toUrl] for a serialiser resolved by the caller, rather than reified at the call site. */
  public fun <T : Any> toUrl(serializer: KSerializer<T>, deepLink: T): String =
    "$urlPrefix${format.encodeToPath(serializer, deepLink)}"
}

/**
 * Prefix `toUrl` emits ahead of the encoded path, which always starts with `/`.
 *
 * A custom scheme wins over `http`/`https` whatever the iteration order, because `schemes` is a
 * `Set` whose order is not part of its contract: emitting the first entry blindly would turn
 * `setOf("https", "myapp")` into `https://payments/abc`, naming `payments` as the host.
 */
private fun buildUrlPrefix(schemes: Set<String>, hosts: Set<String>): String {
  val customScheme = schemes.firstOrNull { it !in HIERARCHICAL_SCHEMES }
  if (customScheme != null) return "$customScheme:/"
  return "${schemes.first()}://${hosts.first()}"
}

/** Thrown when two routes register path patterns that could match the same URL. */
public class DeepLinkCollisionException(
  public val pattern: String,
  public val existingRoute: String,
  public val newRoute: String,
) : IllegalStateException(
  "Deep link collision detected. Pattern '$pattern' conflicts with a pattern already registered " +
    "by '$existingRoute', so '$newRoute' cannot be registered.",
)
