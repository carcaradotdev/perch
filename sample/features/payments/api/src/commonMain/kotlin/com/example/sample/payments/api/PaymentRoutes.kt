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
