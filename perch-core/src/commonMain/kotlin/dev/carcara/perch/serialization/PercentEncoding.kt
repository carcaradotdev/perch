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

package dev.carcara.perch.serialization

/**
 * The RFC 3986 unreserved set. Every other byte is percent-encoded, which over-encodes the
 * sub-delimiters a path segment could legally carry raw: over-encoding costs a few characters and
 * decodes back to the same string, under-encoding changes what the URL means.
 */
private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

private const val HEX_DIGITS = "0123456789ABCDEF"

private const val HEX_RADIX = 16

private const val BYTE_MASK = 0xFF

/**
 * Percent-encodes [value] as UTF-8, one segment or one query component at a time.
 *
 * Called on a single path segment, never on a whole path: a `/` inside a parameter's value is
 * encoded to `%2F` precisely so it does not become a segment boundary on the way back in.
 */
internal fun percentEncode(value: String): String {
  if (value.all { it in UNRESERVED }) return value

  val encoded = StringBuilder(value.length)
  for (byte in value.encodeToByteArray()) {
    val code = byte.toInt() and BYTE_MASK
    val char = code.toChar()
    if (char in UNRESERVED) {
      encoded.append(char)
    } else {
      encoded.append('%')
      encoded.append(HEX_DIGITS[code shr 4])
      encoded.append(HEX_DIGITS[code and 0x0F])
    }
  }
  return encoded.toString()
}

/**
 * Reverses [percentEncode].
 *
 * A `%` not followed by two hex digits is kept literal and invalid UTF-8 becomes the replacement
 * character. A deep link arrives from outside the app, so a malformed one has to produce a value
 * rather than an exception, and whether it matches a route is the ordinary question.
 *
 * `+` is left alone. It means a space only in `application/x-www-form-urlencoded`, which is a form
 * body's encoding, not a URL's.
 */
internal fun percentDecode(value: String): String {
  if ('%' !in value) return value

  val bytes = ArrayList<Byte>(value.length)
  var index = 0
  while (index < value.length) {
    val hexHigh = value.getOrNull(index + 1)?.digitToIntOrNull(HEX_RADIX)
    val hexLow = value.getOrNull(index + 2)?.digitToIntOrNull(HEX_RADIX)
    if (value[index] == '%' && hexHigh != null && hexLow != null) {
      bytes.add(((hexHigh shl 4) or hexLow).toByte())
      index += 3
      continue
    }
    // The whole run up to the next escape at once, so a surrogate pair is encoded as the one code
    // point it is rather than as two unpaired halves.
    val nextEscape = value.indexOf('%', startIndex = index + 1)
    val runEnd = if (nextEscape < 0) value.length else nextEscape
    value.substring(index, runEnd).encodeToByteArray().forEach { bytes.add(it) }
    index = runEnd
  }
  return bytes.toByteArray().decodeToString()
}
