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
 * Compose Destinations, where the deep link maps onto generated types.
 *
 * Here the destinations do not exist until KSP has read the `@Destination` composables below and
 * written `CdHomeScreenDestination` and `CdPaymentScreenDestination` out. They are code generated
 * from the UI, so nothing outside this module can name them, and the mapping has to live on this
 * side — the same reason Voyager's does.
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

/** The route-to-direction mapping. The `Direction`s on the right are generated, not written. */
private fun Any?.toDirection(): Direction? = when (this) {
  is HomeLink -> CdHomeScreenDestination
  is PaymentLink -> CdPaymentScreenDestination(id = id)
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
    Button(onClick = { navigator.navigate(CdPaymentScreenDestination(id = "from-in-app")) }) {
      Text("Navigate to a payment")
    }
  }
}

@Destination<RootGraph>
@Composable
fun CdPaymentScreen(id: String) {
  DestinationBody(
    screen = "Payment",
    detail = "id = $id",
    note = "PaymentLink.id became this destination's argument. Back returns to Home.",
  )
}
