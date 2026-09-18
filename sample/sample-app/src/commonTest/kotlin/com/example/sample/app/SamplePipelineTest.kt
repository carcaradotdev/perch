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

package com.example.sample.app

import com.example.sample.home.api.HomeDeepLink
import com.example.sample.navigation.SampleRoute
import com.example.sample.payments.api.PaymentRoutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SamplePipelineTest {

  @Test
  fun `aggregation reaches every feature and not just the first`() {
    val parser = sampleParser()

    assertIs<HomeDeepLink>(parser.parse("sample://home"))
    assertIs<PaymentRoutes.Details>(parser.parse("sample://payments/x"))
    assertIs<PaymentRoutes.Approvals>(parser.parse("sample://payment-approvals"))
  }

  @Test
  fun `a path parameter survives the whole pipeline`() {
    val parsed = sampleParser().parse("sample://payments/abc123")

    assertIs<PaymentRoutes.Details>(parsed)
    assertEquals("abc123", parsed.id)
  }

  @Test
  fun `narrowing to the app's own supertype types everything past the cast`() {
    val parser = sampleParser()

    // `SampleRoute` cannot be sealed - its implementations live in other modules - so this `when`
    // keeps an `else`. What the cast buys is that every branch below it is a route type, and that
    // "not one of ours" is one branch rather than a condition repeated at every call site.
    fun landingFor(url: String) = when (val route = parser.parse(url) as? SampleRoute) {
      is HomeDeepLink -> "home"
      is PaymentRoutes.Details -> "payment ${route.id}"
      is PaymentRoutes.Approvals -> "approvals"
      else -> "not a Perch route"
    }

    assertEquals("approvals", landingFor("sample://payment-approvals"))
    assertEquals("payment abc123", landingFor("sample://payments/abc123"))
    assertEquals("home", landingFor("sample://home"))
    assertEquals("not a Perch route", landingFor("sample://nothing-here"))
  }

  @Test
  fun `an unconfigured host does not resolve`() {
    assertNull(sampleParser().parse("https://evil.example/payments/abc123"))
  }
}
