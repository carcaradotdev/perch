package com.example.sample.routes

import dev.carcara.perch.DeepLinkParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The single-module shape: a module that declares routes registers them itself, with no aggregator
 * anywhere in the build.
 *
 * `registerDeepLinks()` is what the Perch KSP processor writes into this module's own
 * `commonMain`, so this file does not compile unless `dev.carcara.perch` has put the generated
 * directory on the compile path. `sample-app` covers the other shape, where an aggregator collects
 * this module's manifest instead.
 */
class ModuleRegistrationTest {

  @Test
  fun `registerDeepLinks registers every route this module declares`() {
    val parser = DeepLinkParser(schemes = setOf("sample")).apply { registerDeepLinks() }

    assertIs<HomeLink>(parser.parse("sample://home"))

    val payment = parser.parse("sample://payments/abc123")
    assertIs<PaymentLink>(payment)
    assertEquals("abc123", payment.id)
  }
}
