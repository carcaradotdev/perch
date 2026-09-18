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
 * Derived from io/ktor/tests/resources/ResourceUrlBuilderTest.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch.serialization

import dev.carcara.perch.DeepLink
import dev.carcara.perch.DeepLinkSerializationException
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PathBuilderTest {

  private val format = DeepLinkFormat()

  private inline fun <reified T> path(value: T): String =
    format.encodeToPath(serializer<T>(), value)

  @DeepLink("resource/{id}/")
  data class SimpleResource(val id: Int)

  @Test
  fun `a placeholder takes the value of the property with its name`() {
    assertEquals("/resource/123/", path(SimpleResource(123)))
  }

  @DeepLink("resource/{id}")
  data class ResourceWithQuery(val id: Int, val key: String)

  @Test
  fun `a property with no placeholder becomes a query parameter`() {
    assertEquals("/resource/123?key=456", path(ResourceWithQuery(123, "456")))
  }

  @DeepLink("resource/{id}")
  data class ResourceWithQueryList(val id: Int, val key: List<String>)

  @Test
  fun `a list property repeats its query parameter`() {
    assertEquals("/resource/123?key=456&key=789", path(ResourceWithQueryList(123, listOf("456", "789"))))
  }

  @DeepLink("resource/{ids...}")
  data class ResourceWithWildcard(val ids: List<String>)

  @Test
  fun `a tailcard spreads its values across segments and disappears when empty`() {
    assertEquals("/resource/456/789", path(ResourceWithWildcard(listOf("456", "789"))))
    assertEquals("/resource", path(ResourceWithWildcard(emptyList())))
  }

  @DeepLink("resource/{id?}")
  data class ResourceWithNullable(val id: Boolean?)

  @Test
  fun `an optional placeholder disappears when its value is null`() {
    assertEquals("/resource/true", path(ResourceWithNullable(true)))
    assertEquals("/resource", path(ResourceWithNullable(null)))
  }

  @DeepLink("user/{user}")
  data class NestedResource(val user: String, val parent: SimpleResource)

  @Test
  fun `a nested route renders its parent's path first`() {
    assertEquals("/resource/123/user/me", path(NestedResource("me", SimpleResource(123))))
  }

  @DeepLink("user/{id}")
  class ResourceWithoutParameter

  @Test
  fun `a placeholder with no property to fill it fails by name`() {
    val failure = assertFailsWith<DeepLinkSerializationException> { path(ResourceWithoutParameter()) }
    assertEquals("Expect exactly one parameter with name: id, but found 0", failure.message)
  }

  @DeepLink("user/{id}")
  class ResourceWithExtraParameter(val id: List<String>)

  @Test
  fun `a required placeholder given several values fails by name`() {
    val failure = assertFailsWith<DeepLinkSerializationException> {
      path(ResourceWithExtraParameter(listOf("1", "2")))
    }
    assertEquals("Expect exactly one parameter with name: id, but found 2", failure.message)
  }

  @DeepLink("user/{id?}")
  class ResourceWithExtraNullableParameter(val id: List<String>)

  @Test
  fun `an optional placeholder given several values fails by name`() {
    val failure = assertFailsWith<DeepLinkSerializationException> {
      path(ResourceWithExtraNullableParameter(listOf("1", "2")))
    }
    assertEquals("Expect zero or one parameter with name: id, but found 2", failure.message)
  }

  @DeepLink("files/{name}")
  data class FileResource(val name: String, val note: String)

  @Test
  fun `a value carrying reserved characters is percent-encoded per segment`() {
    assertEquals(
      "/files/a%2Fb%20c?note=%C3%A9%3D1",
      path(FileResource(name = "a/b c", note = "é=1")),
    )
  }
}
