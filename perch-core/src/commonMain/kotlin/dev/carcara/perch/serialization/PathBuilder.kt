/*
 * Derived from io/ktor/resources/UrlBuilder.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import dev.carcara.perch.DeepLinkSerializationException
import kotlinx.serialization.KSerializer

/**
 * Renders [value] as the path-and-query part of a URL, leading slash included: `/payments/abc?x=1`.
 *
 * Each property fills the placeholder of its own name; whatever is left over becomes a query
 * parameter, in declaration order. Path segments and query components are percent-encoded
 * individually, which is what [dev.carcara.perch.DeepLinkParser.parse] undoes on the way back in.
 *
 * @throws DeepLinkSerializationException when a required placeholder has no value to fill it, or
 * an optional one has more than one.
 */
internal fun <T> DeepLinkFormat.encodeToPath(serializer: KSerializer<T>, value: T): String {
  val parameters = encodeToParameters(serializer, value)
  val pattern = encodeToPathPattern(serializer)

  val usedForPath = mutableSetOf<String>()
  val segments = pattern.split("/").flatMap { segment ->
    if (!segment.startsWith('{') || !segment.endsWith('}')) return@flatMap listOf(segment)
    fillPlaceholder(segment, parameters, usedForPath)
  }

  val query = parameters.filterNames { it !in usedForPath }
    .entries()
    .flatMap { (name, values) -> values.map { "${percentEncode(name)}=${percentEncode(it)}" } }
    .joinToString("&")

  val path = segments.joinToString("/") { percentEncode(it) }
  return if (query.isEmpty()) "/$path" else "/$path?$query"
}

/** The values a `{name}`, `{name?}` or `{name...}` placeholder expands to, marking the name used. */
private fun fillPlaceholder(
  segment: String,
  parameters: DeepLinkParameters,
  usedForPath: MutableSet<String>,
): List<String> {
  val placeholder = segment.substring(1, segment.lastIndex)
  return when {
    placeholder.endsWith('?') -> {
      val name = placeholder.dropLast(1)
      val values = parameters.getAll(name) ?: return emptyList()
      if (values.size > 1) {
        throw DeepLinkSerializationException(
          "Expect zero or one parameter with name: $name, but found ${values.size}",
        )
      }
      usedForPath += name
      values
    }

    placeholder.endsWith("...") -> {
      val name = placeholder.dropLast(3)
      usedForPath += name
      parameters.getAll(name).orEmpty()
    }

    else -> {
      val values = parameters.getAll(placeholder)
      if (values == null || values.size != 1) {
        throw DeepLinkSerializationException(
          "Expect exactly one parameter with name: $placeholder, but found ${values?.size ?: 0}",
        )
      }
      usedForPath += placeholder
      values
    }
  }
}
