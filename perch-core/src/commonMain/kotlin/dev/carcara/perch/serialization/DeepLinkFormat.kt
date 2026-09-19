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
 * Derived from io/ktor/resources/serialization/ResourcesFormat.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import dev.carcara.perch.DeepLink
import dev.carcara.perch.DeepLinkSerializationException
import dev.carcara.perch.joinDeepLinkPattern
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Reads a route's `@DeepLink` path pattern off its serialiser, and moves the route between its
 * object form and its parameters.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class DeepLinkFormat(
  override val serializersModule: SerializersModule = EmptySerializersModule(),
) : SerialFormat {

  /**
   * The path pattern of [serializer]'s route, with a nested route's pattern appended to its
   * parent's.
   *
   * @throws DeepLinkSerializationException when the class carries no [DeepLink] annotation, or
   * when it declares more than one property whose type is itself a route.
   */
  fun <T> encodeToPathPattern(serializer: KSerializer<T>): String {
    val segments = mutableListOf<String>()

    var current: SerialDescriptor? = serializer.descriptor
    while (current != null) {
      val descriptor = current
      segments += descriptor.annotations.filterIsInstance<DeepLink>().firstOrNull()?.path
        ?: throw DeepLinkSerializationException(
          "${descriptor.serialName} is not annotated @DeepLink, so it has no path pattern",
        )

      // A property whose own type is a route is this route's parent. There is at most one: a route
      // reached by two different paths would have two patterns and no way to choose between them.
      val parents = descriptor.elementDescriptors.filter { element ->
        element.annotations.any { it is DeepLink }
      }
      if (parents.size > 1) {
        throw DeepLinkSerializationException(
          "There are multiple parents for deep link ${descriptor.serialName}",
        )
      }
      current = parents.firstOrNull()
    }

    return joinDeepLinkPattern(segments)
  }

  fun <T> encodeToParameters(serializer: KSerializer<T>, value: T): DeepLinkParameters {
    val encoder = ParametersEncoder(serializersModule)
    encoder.encodeSerializableValue(serializer, value)
    return encoder.parameters
  }

  fun <T> decodeFromParameters(
    deserializer: KSerializer<T>,
    parameters: DeepLinkParameters,
  ): T {
    val decoder = ParametersDecoder(serializersModule, parameters, emptyList())
    return decoder.decodeSerializableValue(deserializer)
  }
}
