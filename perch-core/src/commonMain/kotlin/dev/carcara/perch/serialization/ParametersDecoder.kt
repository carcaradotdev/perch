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
 * Derived from io/ktor/resources/serialization/Decoders.kt in Ktor.
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import dev.carcara.perch.DeepLinkSerializationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule

/** Rebuilds a route from name-to-value pairs, one property at a time. */
@OptIn(ExperimentalSerializationApi::class)
internal class ParametersDecoder(
  override val serializersModule: SerializersModule,
  private val parameters: DeepLinkParameters,
  elementNames: Iterable<String>,
) : StringBackedDecoder() {

  private val parameterNames = elementNames.iterator()
  private lateinit var currentName: String

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    while (parameterNames.hasNext()) {
      currentName = parameterNames.next()
      val elementIndex = descriptor.getElementIndex(currentName)
      val elementKind = descriptor.getElementDescriptor(elementIndex).kind
      val isPrimitive = elementKind is PrimitiveKind
      val isEnum = elementKind is SerialKind.ENUM
      // A property that is neither primitive nor enum is a nested structure, which gets decoded
      // whether or not a parameter of its own name arrived. A primitive with no parameter is
      // skipped, so its default (or null) stands.
      if (!(isPrimitive || isEnum) || parameters.contains(currentName)) {
        return elementIndex
      }
    }
    return CompositeDecoder.DECODE_DONE
  }

  override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
    if (descriptor.kind == StructureKind.LIST) {
      return ListLikeDecoder(serializersModule, parameters, currentName)
    }
    return ParametersDecoder(serializersModule, parameters, descriptor.elementNames)
  }

  override fun decodeString(): String = parameters[currentName]
    ?: throw DeepLinkSerializationException("No value for parameter '$currentName'")

  override fun decodeNotNullMark(): Boolean = parameters.contains(currentName)
}

/** Decodes the repeated values of one name into a list, for a tailcard or a repeated parameter. */
@OptIn(ExperimentalSerializationApi::class)
private class ListLikeDecoder(
  override val serializersModule: SerializersModule,
  private val parameters: DeepLinkParameters,
  private val parameterName: String,
) : StringBackedDecoder() {

  private var currentIndex = -1

  private val elements: List<String> = parameters.getAll(parameterName).orEmpty()

  override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
    if (++currentIndex == elements.size) {
      return CompositeDecoder.DECODE_DONE
    }
    return currentIndex
  }

  override fun decodeString(): String = elements[currentIndex]

  override fun decodeNotNullMark(): Boolean = parameters.contains(parameterName)
}

/**
 * What both decoders above have in common: a route parameter arrives as text, so every primitive
 * and every enum is that text reinterpreted. Only where the text comes from differs, which is why
 * [decodeString] is the one thing a subclass has to answer.
 */
@OptIn(ExperimentalSerializationApi::class)
internal abstract class StringBackedDecoder : AbstractDecoder() {

  abstract override fun decodeString(): String

  override fun decodeBoolean(): Boolean = decodeString().toBoolean()

  override fun decodeByte(): Byte = decodeString().toByte()

  override fun decodeChar(): Char = decodeString().first()

  override fun decodeDouble(): Double = decodeString().toDouble()

  override fun decodeFloat(): Float = decodeString().toFloat()

  override fun decodeInt(): Int = decodeString().toInt()

  override fun decodeLong(): Long = decodeString().toLong()

  override fun decodeShort(): Short = decodeString().toShort()

  override fun decodeNull(): Nothing? = null

  override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
    enumIndexOf(enumDescriptor, decodeString())
}

@OptIn(ExperimentalSerializationApi::class)
private fun enumIndexOf(enumDescriptor: SerialDescriptor, name: String): Int {
  val index = enumDescriptor.getElementIndex(name)
  if (index == CompositeDecoder.UNKNOWN_NAME) {
    throw DeepLinkSerializationException(
      "${enumDescriptor.serialName} does not contain element with name '$name'",
    )
  }
  return index
}
