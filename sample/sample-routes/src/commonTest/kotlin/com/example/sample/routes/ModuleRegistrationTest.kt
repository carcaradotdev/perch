package com.example.sample.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The single-module shape: a module that declares routes registers them itself, with no aggregator
 * anywhere in the build.
 *
 * `perchModuleParser()` is what the Perch KSP processor writes into this module's own
 * `commonMain`, so this file does not compile unless `dev.carcara.perch` has put the generated
 * directory on the compile path. `sample-app` covers the other shape, where an aggregator collects
 * this module's manifest instead.
 */
class ModuleRegistrationTest {

  @Test
  fun `perchModuleParser carries every route this module declares`() {
    val parser = perchModuleParser(schemes = setOf("sample"))

    assertIs<HomeLink>(parser.parse("sample://home"))

    val payment = parser.parse("sample://payments/abc123")
    assertIs<PaymentLink>(payment)
    assertEquals("abc123", payment.id)
  }
}
