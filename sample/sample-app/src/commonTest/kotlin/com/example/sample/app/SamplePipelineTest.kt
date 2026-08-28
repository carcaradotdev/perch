package com.example.sample.app

import com.example.sample.routes.HomeLink
import com.example.sample.routes.PaymentLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SamplePipelineTest {

  @Test
  fun `the generated registration resolves a route declared in another module`() {
    val parsed = sampleParser().parse("sample://payments/abc123")

    assertIs<PaymentLink>(parsed)
    assertEquals("abc123", parsed.id)
  }

  @Test
  fun `every declared route is registered`() {
    val parser = sampleParser()

    assertIs<HomeLink>(parser.parse("sample://home"))
    assertIs<PaymentLink>(parser.parse("sample://payments/x"))
  }

  @Test
  fun `an unconfigured host does not resolve`() {
    assertNull(sampleParser().parse("https://evil.example/payments/abc123"))
  }
}
