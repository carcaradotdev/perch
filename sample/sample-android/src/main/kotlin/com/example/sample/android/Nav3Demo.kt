package com.example.sample.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.sample.home.api.HomeDeepLink
import com.example.sample.payments.api.PaymentRoutes

/**
 * Navigation 3, where the deep link needs no adapter at all.
 *
 * `NavBackStack` holds `NavKey`s, and sample-routes' routes implement `NavKey` — which they are
 * free to do because Perch demands no supertype of its own. So the object `parse` returned is
 * already the object the back stack accepts: pushing it is an `add`, and matching on it is the
 * same `entry<PaymentLink>` an in-app navigation would use. There is no second set of types
 * mirroring the routes, and no mapping function to keep in step with them.
 */
@Composable
fun Nav3Demo(route: Any?) {
  // `rememberNavBackStack`, not `remember`: the latter dies with the composition, so a rotation
  // would drop the reader back to Home. This one saves and restores the stack, and it is where
  // `@DeepLink` being `@MetaSerializable` pays off - nav3 asks that keys be serializable, and the
  // routes are, without a second annotation on them.
  val backStack = when (val deepLinked = route as? NavKey) {
    null, is HomeDeepLink -> rememberNavBackStack(HomeDeepLink)
    else -> rememberNavBackStack(HomeDeepLink, deepLinked)
  }

  NavDisplay(
    backStack = backStack,
    modifier = Modifier.fillMaxSize(),
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider<NavKey> {
      entry<HomeDeepLink> {
        DestinationBody(
          screen = "Home",
          detail = "The start of the back stack.",
          note = "Opened from HomeLink, or as the entry this demo always starts on.",
        ) {
          Button(onClick = { backStack.add(PaymentRoutes.Details("from-in-app")) }) {
            Text("Push a payment")
          }
        }
      }
      entry<PaymentRoutes.Approvals> {
        DestinationBody(
          screen = "Approvals",
          detail = "The payments waiting on someone.",
          note = "The payments feature owns two links; this is the one with no parameters.",
        )
      }
      entry<PaymentRoutes.Details> { key ->
        DestinationBody(
          screen = "Payment",
          detail = "id = ${key.id}",
          note = "The NavEntry key here is the very object parse() returned. Back pops it.",
        )
      }
    },
  )
}
