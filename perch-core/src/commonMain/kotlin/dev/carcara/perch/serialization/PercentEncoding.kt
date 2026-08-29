package dev.carcara.perch.serialization

/**
 * The RFC 3986 unreserved set. Every other byte is percent-encoded, which over-encodes the
 * sub-delimiters a path segment could legally carry unescaped. Over-encoding costs a few
 * characters and decodes back to the same string; under-encoding produces a URL that means
 * something different from the value it was built out of.
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
 * A `%` that is not followed by two hex digits is kept as a literal `%` rather than rejected, and
 * bytes that do not form valid UTF-8 become the replacement character. A deep link arrives from
 * outside the app, so a malformed one has to produce a value rather than an exception; whether
 * that value matches a route is then the ordinary matching question.
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
    // Copy the whole run up to the next escape in one go, so a surrogate pair is encoded as the
    // one code point it is rather than as two unpaired halves.
    val nextEscape = value.indexOf('%', startIndex = index + 1)
    val runEnd = if (nextEscape < 0) value.length else nextEscape
    value.substring(index, runEnd).encodeToByteArray().forEach { bytes.add(it) }
    index = runEnd
  }
  return bytes.toByteArray().decodeToString()
}
