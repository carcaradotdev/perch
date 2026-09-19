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

/*
 * Derived from io/ktor/resources/UrlBuilder.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import dev.carcara.perch.DeepLinkSerializationException
import dev.carcara.perch.PathSegment
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
    fill(PathSegment.parse(segment), parameters, usedForPath)
  }

  val query = parameters.filterNames { it !in usedForPath }
    .entries()
    .flatMap { (name, values) -> values.map { "${percentEncode(name)}=${percentEncode(it)}" } }
    .joinToString("&")

  val path = segments.joinToString("/") { percentEncode(it) }
  return if (query.isEmpty()) "/$path" else "/$path?$query"
}

/**
 * The path elements [segment] expands to, marking any name it consumed as used.
 *
 * The grammar comes from [PathSegment.parse], the same classification `parse` matches URLs with.
 * Recognising the placeholder forms a second time here is how the two directions drift apart.
 */
private fun fill(
  segment: PathSegment,
  parameters: DeepLinkParameters,
  usedForPath: MutableSet<String>,
): List<String> = when (segment) {
  is PathSegment.Constant -> listOf(segment.value)

  is PathSegment.OptionalParameter -> {
    val values = parameters.getAll(segment.name).orEmpty()
    if (values.size > 1) {
      throw DeepLinkSerializationException(
        "Expect zero or one parameter with name: ${segment.name}, but found ${values.size}",
      )
    }
    if (values.isNotEmpty()) usedForPath += segment.name
    values
  }

  is PathSegment.Tailcard -> {
    usedForPath += segment.name
    parameters.getAll(segment.name).orEmpty()
  }

  is PathSegment.Parameter -> {
    val values = parameters.getAll(segment.name)
    if (values == null || values.size != 1) {
      throw DeepLinkSerializationException(
        "Expect exactly one parameter with name: ${segment.name}, but found ${values?.size ?: 0}",
      )
    }
    usedForPath += segment.name
    values
  }
}
