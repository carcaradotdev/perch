/*
 * Derived from io/ktor/resources/serialization/ParametersEncoder.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.modules.SerializersModule

/** Flattens a route into name-to-value pairs, one per property, keeping list elements together. */
@OptIn(ExperimentalSerializationApi::class)
internal class ParametersEncoder(
  override val serializersModule: SerializersModule,
) : AbstractEncoder() {

  private val builder = DeepLinkParameters.Builder()

  val parameters: DeepLinkParameters
    get() = builder.build()

  private lateinit var nextElementName: String

  override fun encodeValue(value: Any) {
    builder.append(nextElementName, value.toString())
  }

  override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
    // A list's elements all belong to the name of the property holding the list, so the name is
    // left standing rather than replaced with the element's index.
    if (descriptor.kind != StructureKind.LIST) {
      nextElementName = descriptor.getElementName(index)
    }
    return true
  }

  override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
    encodeValue(enumDescriptor.getElementName(index))
  }

  override fun encodeNull() {
    // A null property contributes no parameter at all, which is what makes it absent from the URL
    // rather than present and empty.
  }
}
