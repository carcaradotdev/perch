package dev.carcara.perch

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DeepLinkParserSchemeHostTest {

  private fun parser(
    schemes: Set<String> = setOf("acme"),
    hosts: Set<String> = emptySet(),
  ) = DeepLinkParser(schemes = schemes, hosts = hosts).apply { register<PaymentLink>() }

  @Test
  fun `a registered scheme parses`() {
    val result = parser().parse("acme://payments/abc123")

    assertIs<PaymentLink>(result)
    assertEquals("abc123", result.id)
  }

  @Test
  fun `an unregistered scheme does not parse`() {
    assertNull(parser().parse("evil://payments/abc123"))
  }

  @Test
  fun `a schemeless path parses against the configured schemes`() {
    val result = parser().parse("payments/abc123")

    assertIs<PaymentLink>(result)
  }

  @Test
  fun `a leading slash is accepted`() {
    assertIs<PaymentLink>(parser().parse("/payments/abc123"))
  }

  @Test
  fun `an https url from a configured host parses`() {
    val subject = parser(schemes = setOf("acme", "https"), hosts = setOf("acme.com"))

    assertIs<PaymentLink>(subject.parse("https://acme.com/payments/abc123"))
  }

  @Test
  fun `an https url from an unconfigured host does not parse`() {
    val subject = parser(schemes = setOf("acme", "https"), hosts = setOf("acme.com"))

    assertNull(subject.parse("https://evil.example/payments/abc123"))
  }

  @Test
  fun `an empty host set accepts any host`() {
    val subject = parser(schemes = setOf("acme", "https"), hosts = emptySet())

    assertIs<PaymentLink>(subject.parse("https://anything.example/payments/abc123"))
  }

  @Test
  fun `host matching is case insensitive`() {
    val subject = parser(schemes = setOf("https"), hosts = setOf("acme.com"))

    assertIs<PaymentLink>(subject.parse("https://ACME.com/payments/abc123"))
  }

  @Test
  fun `scheme matching is case insensitive`() {
    assertIs<PaymentLink>(parser().parse("ACME://payments/abc123"))
  }

  @Test
  fun `two parsers keep separate registries`() {
    val registered = DeepLinkParser(schemes = setOf("acme")).apply { register<PaymentLink>() }
    val empty = DeepLinkParser(schemes = setOf("acme"))

    assertIs<PaymentLink>(registered.parse("acme://payments/abc123"))
    assertNull(empty.parse("acme://payments/abc123"))
  }

  @Test
  fun `toUrl uses the first configured scheme`() {
    val subject = DeepLinkParser(schemes = setOf("acme", "https"))

    assertEquals("acme://payments/abc123", subject.toUrl(PaymentLink("abc123")))
  }

  // An https authority is a host whatever it looks like. These pin the rule that the scheme, not
  // the shape of the authority, decides whether a URL has a host to check against `hosts`.

  @Test
  fun `a single label https host is still a host`() {
    val subject = parser(schemes = setOf("acme", "https"), hosts = setOf("acme.com"))

    assertNull(subject.parse("https://payments/abc123"))
  }

  @Test
  fun `an empty https authority is rejected`() {
    val guarded = parser(schemes = setOf("acme", "https"), hosts = setOf("acme.com"))
    val open = parser(schemes = setOf("acme", "https"), hosts = emptySet())

    assertNull(guarded.parse("https:///payments/abc123"))
    assertNull(open.parse("https:///payments/abc123"))
  }

  @Test
  fun `a port is not part of the host`() {
    val subject = parser(schemes = setOf("https"), hosts = setOf("acme.com"))

    assertIs<PaymentLink>(subject.parse("https://acme.com:8443/payments/abc123"))
  }

  @Test
  fun `userinfo is not part of the host`() {
    val subject = parser(schemes = setOf("https"), hosts = setOf("acme.com"))

    assertIs<PaymentLink>(subject.parse("https://evil.example@acme.com/payments/abc123"))
    assertNull(subject.parse("https://acme.com@evil.example/payments/abc123"))
  }

  @Test
  fun `an uppercase host with userinfo and a port matches`() {
    val subject = parser(schemes = setOf("https"), hosts = setOf("acme.com"))

    assertIs<PaymentLink>(subject.parse("https://USER@ACME.COM:8443/payments/abc123"))
  }

  @Test
  fun `an ipv6 literal host keeps its brackets`() {
    val subject = parser(schemes = setOf("https"), hosts = setOf("[::1]"))

    assertIs<PaymentLink>(subject.parse("https://[::1]:8080/payments/abc123"))
  }

  // A custom scheme has no authority to interpret: its first path element merely sits where a
  // host would be in a hierarchical URL.

  @Test
  fun `a custom scheme keeps a dotted first path segment`() {
    val subject = DeepLinkParser(schemes = setOf("acme")).apply { register<DottedLink>() }

    assertIs<DottedLink>(subject.parse("acme://pay.co/abc123"))
  }

  @Test
  fun `a custom scheme never consults the host set`() {
    val subject = parser(schemes = setOf("acme"), hosts = setOf("acme.com"))

    assertIs<PaymentLink>(subject.parse("acme://payments/abc123"))
  }

  @Test
  fun `a backslash terminates the authority`() {
    val subject = parser(schemes = setOf("https"), hosts = setOf("acme.com"))

    // A browser reads the host here as evil.example, because the WHATWG URL standard treats a
    // backslash as a slash for a special scheme. Perch must not read it as acme.com.
    assertNull(subject.parse("https://evil.example\\@acme.com/payments/abc123"))
  }

  // A fragment belongs to neither the path nor the query, in any scheme.

  @Test
  fun `a fragment is not part of a path parameter`() {
    val subject = parser(schemes = setOf("https"), hosts = setOf("acme.com"))

    val result = subject.parse("https://acme.com/payments/abc123#frag")

    assertIs<PaymentLink>(result)
    assertEquals("abc123", result.id)
  }

  @Test
  fun `a fragment after a query string is stripped`() {
    val subject = searchParser()

    val result = subject.parse("https://acme.com/search?query=coffee#frag")

    assertIs<SearchLink>(result)
    assertEquals("coffee", result.query)
  }

  @Test
  fun `a fragment containing a query string does not leak into the query`() {
    val subject = searchParser()

    val result = subject.parse("https://acme.com/search?query=coffee#x?query=evil")

    assertIs<SearchLink>(result)
    assertEquals("coffee", result.query)
  }

  @Test
  fun `a fragment cannot inject a query parameter`() {
    val subject = searchParser()

    // The URL carries no query at all. Splitting the query off before the fragment would read
    // `query=evil` out of the fragment and hand the route a parameter it was never given.
    assertNull(subject.parse("https://acme.com/search#x?query=evil"))
  }

  private fun searchParser() =
    DeepLinkParser(schemes = setOf("https"), hosts = setOf("acme.com"))
      .apply { register<SearchLink>() }
}

@Serializable
@Resource("/payments/{id}")
private class PaymentLink(val id: String) : DeepLinkTarget {
  override val requiresAuth: Boolean get() = true
}

@Serializable
@Resource("/pay.co/{id}")
private class DottedLink(val id: String) : DeepLinkTarget {
  override val requiresAuth: Boolean get() = true
}

@Serializable
@Resource("/search")
private class SearchLink(val query: String) : DeepLinkTarget {
  override val requiresAuth: Boolean get() = true
}
