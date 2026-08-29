/*
 * Derived from io/ktor/resources/serialization/ResourcesFormat.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import dev.carcara.perch.DeepLink
import dev.carcara.perch.DeepLinkSerializationException
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
    val path = StringBuilder()

    var current: SerialDescriptor? = serializer.descriptor
    while (current != null) {
      val segment = current.annotations.filterIsInstance<DeepLink>().firstOrNull()?.path
        ?: throw DeepLinkSerializationException(
          "${current.serialName} is not annotated @DeepLink, so it has no path pattern",
        )
      val needsSlash = path.isNotEmpty() && !path.startsWith('/') && !segment.endsWith('/')
      if (needsSlash) {
        path.insert(0, '/')
      }
      path.insert(0, segment)

      val parents = current.elementDescriptors.filter { element ->
        element.annotations.any { it is DeepLink }
      }
      if (parents.size > 1) {
        throw DeepLinkSerializationException(
          "There are multiple parents for deep link ${current.serialName}",
        )
      }
      current = parents.firstOrNull()
    }

    if (path.startsWith('/')) {
      path.deleteAt(0)
    }
    return path.toString()
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
