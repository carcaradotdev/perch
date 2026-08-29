package dev.carcara.perch.serialization

import kotlin.test.Test
import kotlin.test.assertEquals

class PercentEncodingTest {

  @Test
  fun `the unreserved set is left alone`() {
    val unreserved = "abcXYZ019-._~"
    assertEquals(unreserved, percentEncode(unreserved))
    assertEquals(unreserved, percentDecode(unreserved))
  }

  @Test
  fun `everything else is encoded and non-ASCII becomes its UTF-8 bytes`() {
    assertEquals("a%2Fb", percentEncode("a/b"))
    assertEquals("a%20b", percentEncode("a b"))
    assertEquals("%3F%26%3D%23", percentEncode("?&=#"))
    assertEquals("%C3%A9", percentEncode("é"))
    assertEquals("%F0%9F%90%A6", percentEncode("🐦"))
  }

  @Test
  fun `decoding reverses encoding including outside the basic plane`() {
    listOf("a/b", "a b", "?&=#", "é", "🐦", "", "plain", "100%", "50%25off")
      .forEach { assertEquals(it, percentDecode(percentEncode(it))) }
  }

  @Test
  fun `lowercase escapes decode too`() {
    assertEquals("é", percentDecode("%c3%a9"))
  }

  @Test
  fun `a percent that starts no escape stays a literal percent`() {
    assertEquals("100%", percentDecode("100%"))
    assertEquals("%zz", percentDecode("%zz"))
    assertEquals("a%b c", percentDecode("a%b%20c"))
  }

  @Test
  fun `a plus stays a plus and is not a space`() {
    assertEquals("a+b", percentDecode("a+b"))
  }

  @Test
  fun `bytes that are not valid UTF-8 decode to the replacement character rather than throwing`() {
    assertEquals("�", percentDecode("%FF"))
  }
}
