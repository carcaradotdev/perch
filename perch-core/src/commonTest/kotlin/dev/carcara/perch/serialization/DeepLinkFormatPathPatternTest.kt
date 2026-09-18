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
 * Derived from io/ktor/tests/resources/PathPatternSerializationTest.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import dev.carcara.perch.DeepLink
import dev.carcara.perch.DeepLinkSerializationException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeepLinkFormatPathPatternTest {

  private val format = DeepLinkFormat()

  @DeepLink("some/path/")
  class SimplePath

  @Test
  fun `a pattern with no parent is the annotation path including its trailing slash`() {
    assertEquals("some/path/", format.encodeToPathPattern(SimplePath.serializer()))
  }

  @DeepLink("parent/{path}")
  class NestedClass {
    @DeepLink("{child}/path")
    data class ChildClass(val parent: NestedClass)
  }

  @Test
  fun `a child's pattern is appended to its parent's`() {
    assertEquals(
      "parent/{path}/{child}/path",
      format.encodeToPathPattern(NestedClass.ChildClass.serializer()),
    )
  }

  @DeepLink("parent/{path}/")
  class NestedClassWithSlash {
    @DeepLink("{child}/path")
    data class ChildClassWithSlash(val parent: NestedClass)

    @DeepLink("/{child}/path")
    data class ChildClassWithoutSlash(val parent: NestedClass)
  }

  @DeepLink("parent/{path}")
  class NestedClassWithoutSlash {
    @DeepLink("/{child}/path")
    data class ChildClassWithSlash(val parent: NestedClass)
  }

  @Test
  fun `joining a parent and a child never doubles the slash between them`() {
    assertEquals(
      "parent/{path}/{child}/path",
      format.encodeToPathPattern(NestedClassWithSlash.ChildClassWithSlash.serializer()),
    )
    assertEquals(
      "parent/{path}/{child}/path",
      format.encodeToPathPattern(NestedClassWithSlash.ChildClassWithoutSlash.serializer()),
    )
    assertEquals(
      "parent/{path}/{child}/path",
      format.encodeToPathPattern(NestedClassWithoutSlash.ChildClassWithSlash.serializer()),
    )
  }

  @DeepLink("/{child}/path")
  data class Container(val child: MultipleParents)

  @DeepLink("/{child}/path")
  data class MultipleParents(
    val parent1: NestedClass,
    val value: String,
    val parent2: NestedClassWithSlash,
  )

  @Test
  fun `a route with two parent properties is rejected`() {
    val failure = assertFailsWith<DeepLinkSerializationException> {
      format.encodeToPathPattern(Container.serializer())
    }

    assertEquals(
      "There are multiple parents for deep link " +
        "dev.carcara.perch.serialization.DeepLinkFormatPathPatternTest.MultipleParents",
      failure.message,
    )
  }

  @Serializable
  class NotARoute

  @Test
  fun `a serialisable class with no DeepLink annotation is rejected by name`() {
    val failure = assertFailsWith<DeepLinkSerializationException> {
      format.encodeToPathPattern(NotARoute.serializer())
    }

    assertEquals(
      "dev.carcara.perch.serialization.DeepLinkFormatPathPatternTest.NotARoute " +
        "is not annotated @DeepLink, so it has no path pattern",
      failure.message,
    )
  }
}
