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

package com.example.sample.payments.api

import com.example.sample.navigation.SampleRoute
import dev.carcara.perch.DeepLink

/**
 * Every link the payments feature owns, grouped under the feature's own sealed type.
 *
 * Grouping is the app's choice, not Perch's: the processor recurses into nested declarations and
 * registers each annotated class by its qualified name, so `PaymentRoutes.Details` is registered
 * exactly as a top-level class would be. What the grouping buys is a `when` over one feature's
 * links that the compiler checks.
 *
 * The two paths do not overlap. `/payments/{id}` matches one segment after `/payments`, so a
 * sibling like `/payments/approvals` would be ambiguous with an id of `approvals` - Perch rejects
 * that pair at build time rather than picking a winner at runtime, which is why the second link
 * sits at `/payment-approvals`.
 */
public sealed interface PaymentRoutes : SampleRoute {

  /** One payment, named by the `{id}` the URL carries. */
  @DeepLink("/payments/{id}")
  public data class Details(public val id: String) : PaymentRoutes

  /** The list of payments waiting on someone. */
  @DeepLink("/payment-approvals")
  public data object Approvals : PaymentRoutes
}
