/*
 * Derived from io/ktor/tests/resources/ParametersSerializationTest.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DeepLinkFormatParametersTest {

  private val format = DeepLinkFormat()

  @Serializable
  data class Primitives(
    @SerialName("intValue")
    val intValueWrongName: Int,
    val floatValue: Float,
    val charValue: Char,
    val stringValue: String,
  )

  @Test
  fun `primitives survive a round trip under their serial names`() {
    val value = Primitives(1, 2.0F, 'a', "value")

    val encoded = format.encodeToParameters(Primitives.serializer(), value)
    val decoded = format.decodeFromParameters(Primitives.serializer(), encoded)

    assertEquals(value, decoded)
    assertEquals("1", encoded["intValue"])
    assertEquals(2.0F, assertNotNull(encoded["floatValue"]).toFloat())
    assertEquals("a", encoded["charValue"])
    assertEquals("value", encoded["stringValue"])
  }

  @Serializable
  data class Nested(val someValue: Int, val child: Primitives)

  @Test
  fun `a nested class flattens into the same parameter set`() {
    val value = Nested(2, Primitives(1, 2.0F, 'a', "value"))

    val encoded = format.encodeToParameters(Nested.serializer(), value)
    val decoded = format.decodeFromParameters(Nested.serializer(), encoded)

    assertEquals(value, decoded)
    assertEquals("2", encoded["someValue"])
    assertEquals("1", encoded["intValue"])
    assertEquals("value", encoded["stringValue"])
  }

  @Serializable
  data class Collections(
    val someValues: List<Double>,
    val child: Primitives,
    val someMoreValues: List<Int>,
    val empty: List<Float>,
  )

  @Test
  fun `a list becomes repeated values under one name and an empty list becomes none`() {
    val value = Collections(
      someValues = listOf(3.1, 5.1),
      child = Primitives(1, 2.0F, 'a', "value1"),
      someMoreValues = listOf(3, 5, 7, 9),
      empty = emptyList(),
    )

    val encoded = format.encodeToParameters(Collections.serializer(), value)
    val decoded = format.decodeFromParameters(Collections.serializer(), encoded)

    assertEquals(value, decoded)
    assertEquals(listOf("3.1", "5.1"), encoded.getAll("someValues"))
    assertEquals(listOf("3", "5", "7", "9"), encoded.getAll("someMoreValues"))
    assertEquals(null, encoded["empty"])
  }

  @Serializable
  data class NullableAndDefaults(
    val someValue: String? = null,
    val someMoreValue1: Boolean = false,
    val someMoreValue2: Boolean = true,
    val someMoreValue3: Short = 3,
    val someMoreValueNullable: Boolean? = null,
  )

  @Test
  fun `a null contributes no parameter and a missing one leaves the default standing`() {
    val value = NullableAndDefaults(null)

    val encoded = format.encodeToParameters(NullableAndDefaults.serializer(), value)
    assertEquals(value, format.decodeFromParameters(NullableAndDefaults.serializer(), encoded))

    val supplied = DeepLinkParameters.build { append("someValue", "value") }
    assertEquals(
      NullableAndDefaults("value", someMoreValue1 = false, someMoreValue2 = true, someMoreValue3 = 3),
      format.decodeFromParameters(NullableAndDefaults.serializer(), supplied),
    )
  }

  enum class Choice { Ab, Cd }

  @Serializable
  data class Enums(
    val enum: Choice,
    val enumDefault: Choice = Choice.Cd,
    val enumNullable: Choice? = null,
  )

  @Test
  fun `an enum travels as its element name`() {
    val value = Enums(Choice.Ab)

    val encoded = format.encodeToParameters(Enums.serializer(), value)
    val supplied = DeepLinkParameters.build { append("enum", "Ab") }

    assertEquals(value, format.decodeFromParameters(Enums.serializer(), encoded))
    assertEquals(value, format.decodeFromParameters(Enums.serializer(), supplied))
    assertEquals("Ab", encoded["enum"])
    assertEquals("Cd", encoded["enumDefault"])
    assertEquals(null, encoded["enumNullable"])
  }

  @Serializable
  data class NestedDefaults(
    val someValue: String,
    val child: Primitives = Primitives(1, 2.0F, 'a', "value1"),
    val childNullable: Primitives? = null,
  )

  @Test
  fun `a nested default is encoded and decodes back to itself`() {
    val value = NestedDefaults("2")

    val encoded = format.encodeToParameters(NestedDefaults.serializer(), value)
    val decoded = format.decodeFromParameters(NestedDefaults.serializer(), encoded)

    assertEquals(value, decoded)
    assertEquals("2", encoded["someValue"])
    assertEquals("1", encoded["intValue"])
    assertEquals("value1", encoded["stringValue"])
  }
}
