package com.example.sample.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.sample.routes.HomeLink
import com.example.sample.routes.PaymentLink
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.CdHomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.CdPaymentScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.spec.Direction
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator

/**
 * Compose Destinations, where the route class becomes the destination's arguments.
 *
 * The destinations here do not exist until KSP has read the `@Destination` composables below and
 * written `CdHomeScreenDestination` and `CdPaymentScreenDestination` out, so nothing outside this
 * module can name them and the choice of which one to open has to be made on this side.
 *
 * What does not have to be made on this side is the argument plumbing. `navArgs = PaymentLink::class`
 * hands Compose Destinations the `@DeepLink` route class as the destination's argument holder, and
 * the generated code takes it from there: `CdPaymentScreenDestination` is a
 * `TypedDestinationSpec<PaymentLink>`, its `invoke` takes a `PaymentLink`, its `argsFrom` rebuilds
 * one out of the back stack entry, and the composable below receives it. So `toDirection` passes
 * the object through rather than copying fields out of it, and adding a parameter to the route is
 * one edit instead of three.
 *
 * Compose Destinations has a deep-link feature of its own, declared per destination and resolved
 * by androidx.navigation. Perch is doing the resolving instead: it decides what URLs mean while
 * they are still URLs, and hands over a `Direction` once the answer is a typed object. The two do
 * not overlap, and this demo never sets `deepLinks` on a `@Destination`.
 */
@Composable
fun DestinationsDemo(route: Any?) {
  val navController = rememberNavController()
  val navigator = navController.rememberDestinationsNavigator()

  LaunchedEffect(route) {
    val direction = route.toDirection()
    if (direction != null && direction != CdHomeScreenDestination) {
      navigator.navigate(direction)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    DestinationsNavHost(navGraph = NavGraphs.root, navController = navController)
  }
}

/**
 * Which destination a route opens. The `Direction`s on the right are generated, not written, and
 * `PaymentLink` goes across whole rather than field by field - see this file's KDoc for why.
 */
private fun Any?.toDirection(): Direction? = when (this) {
  is HomeLink -> CdHomeScreenDestination
  is PaymentLink -> CdPaymentScreenDestination(this)
  else -> null
}

@Destination<RootGraph>(start = true)
@Composable
fun CdHomeScreen(navigator: DestinationsNavigator) {
  DestinationBody(
    screen = "Home",
    detail = "The start destination of the generated graph.",
    note = "Reached from HomeLink, or as the destination this demo always starts on.",
  ) {
    Button(onClick = { navigator.navigate(CdPaymentScreenDestination(PaymentLink("from-in-app"))) }) {
      Text("Navigate to a payment")
    }
  }
}

@Destination<RootGraph>(navArgs = PaymentLink::class)
@Composable
fun CdPaymentScreen(link: PaymentLink) {
  DestinationBody(
    screen = "Payment",
    detail = "id = ${link.id}",
    note = "This screen's arguments are the route object itself. Back returns to Home.",
  )
}
