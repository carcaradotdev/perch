package dev.carcara.perch

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
  fun `a hierarchical scheme with no hosts is rejected at construction`() {
    // Registering https with no hosts means "any website may deep-link into these routes", which
    // is the vulnerability this whole design removes, reached by omitting an optional parameter.
    assertFailsWith<IllegalArgumentException> {
      DeepLinkParser(schemes = setOf("acme", "https"))
    }
  }

  @Test
  fun `a custom scheme with no hosts is fine`() {
    val subject = DeepLinkParser(schemes = setOf("acme")).apply { register<PaymentLink>() }

    assertIs<PaymentLink>(subject.parse("acme://payments/abc123"))
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
  fun `toUrl uses the first custom scheme`() {
    val subject = DeepLinkParser(schemes = setOf("acme", "https"), hosts = setOf("acme.com"))

    assertEquals("acme://payments/abc123", subject.toUrl(PaymentLink("abc123")))
  }

  @Test
  fun `toUrl prefers a custom scheme over a hierarchical one listed first`() {
    // One edit away from the README's own `setOf("myapp", "https")`. Emitting `schemes.first()`
    // here would produce `https://payments/abc123`, putting the route's first path element in the
    // host position, and the same parser would then refuse to parse it back.
    val subject = DeepLinkParser(schemes = setOf("https", "acme"), hosts = setOf("acme.com"))
      .apply { register<PaymentLink>() }

    assertEquals("acme://payments/abc123", subject.toUrl(PaymentLink("abc123")))
    assertIs<PaymentLink>(subject.parse(subject.toUrl(PaymentLink("abc123"))))
  }

  @Test
  fun `toUrl emits a host when every configured scheme is hierarchical`() {
    val subject = DeepLinkParser(schemes = setOf("https"), hosts = setOf("acme.com"))
      .apply { register<PaymentLink>() }

    assertEquals("https://acme.com/payments/abc123", subject.toUrl(PaymentLink("abc123")))
    assertIs<PaymentLink>(subject.parse(subject.toUrl(PaymentLink("abc123"))))
  }

  @Test
  fun `toUrl emits the scheme lowercased`() {
    val subject = DeepLinkParser(schemes = setOf("ACME"))

    assertEquals("acme://payments/abc123", subject.toUrl(PaymentLink("abc123")))
  }

  @Test
  fun `schemes and hosts configured in uppercase still match`() {
    val subject = DeepLinkParser(schemes = setOf("ACME", "HTTPS"), hosts = setOf("ACME.COM"))
      .apply { register<PaymentLink>() }

    assertIs<PaymentLink>(subject.parse("acme://payments/abc123"))
    assertIs<PaymentLink>(subject.parse("https://acme.com/payments/abc123"))
  }

  @Test
  fun `surrounding whitespace is ignored`() {
    val subject = parser(schemes = setOf("https"), hosts = setOf("acme.com"))

    val result = subject.parse("  https://acme.com/payments/abc123  ")

    assertIs<PaymentLink>(result)
    assertEquals("abc123", result.id)
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
    val subject = parser(schemes = setOf("acme", "https"), hosts = setOf("acme.com"))

    assertNull(subject.parse("https:///payments/abc123"))
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

  // `of()` recognises a scheme by "://". A string carrying a scheme in any other form must be
  // rejected outright rather than demoted to a schemeless path, where neither gate would run.
  // A tenant-style route is what makes the difference visible: against `/payments/{id}` these
  // URLs fail on segment count, which is an accident, not a rule.

  @Test
  fun `a scheme without a double slash cannot launder into a schemeless path`() {
    val subject = DeepLinkParser(schemes = setOf("https"), hosts = setOf("acme.com"))
      .apply { register<TenantLink>() }

    assertNull(subject.parse("https:\\evil.example/payments/abc123"))
    assertNull(subject.parse("https:/evil.example/payments/abc123"))
    assertNull(subject.parse("https:evil.example/payments/abc123"))
  }

  @Test
  fun `a schemeless path may carry a colon after its first segment`() {
    val subject = parser()

    val result = subject.parse("payments/a:b")

    assertIs<PaymentLink>(result)
    assertEquals("a:b", result.id)
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
private class PaymentLink(val id: String) : DeepLinkTarget

@Serializable
@Resource("/pay.co/{id}")
private class DottedLink(val id: String) : DeepLinkTarget

@Serializable
@Resource("/search")
private class SearchLink(val query: String) : DeepLinkTarget

@Serializable
@Resource("/{tenant}/payments/{id}")
private class TenantLink(val tenant: String, val id: String) : DeepLinkTarget
