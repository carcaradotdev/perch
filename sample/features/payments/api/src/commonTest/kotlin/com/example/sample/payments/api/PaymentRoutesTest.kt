package com.example.sample.payments.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Both links of a grouped feature reach the generated registration, which is the part a sealed
 * hierarchy could plausibly break: the processor has to recurse into nested declarations to find
 * them at all.
 */
class PaymentRoutesTest {

  @Test
  fun `a nested route with a path parameter resolves`() {
    val parsed = perchModuleParser(schemes = setOf("sample")).parse("sample://payments/abc123")

    assertIs<PaymentRoutes.Details>(parsed)
    assertEquals("abc123", parsed.id)
  }

  @Test
  fun `a nested object route resolves`() {
    val parser = perchModuleParser(schemes = setOf("sample"))

    assertIs<PaymentRoutes.Approvals>(parser.parse("sample://payment-approvals"))
  }
}
