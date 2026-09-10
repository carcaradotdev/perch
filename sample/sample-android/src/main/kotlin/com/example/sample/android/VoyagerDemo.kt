package com.example.sample.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.sample.routes.HomeLink
import com.example.sample.routes.PaymentLink

/**
 * Voyager, where the deep link needs one mapping function.
 *
 * A Voyager `Screen` declares `@Composable fun Content()` — it is the UI, not a description of it.
 * A route module implementing `Screen` would have to depend on Compose and carry the screen's
 * layout, which is not what a shared route module is for. So the routes stay plain and the mapping
 * lives here, in the module that already owns the UI.
 *
 * That is the shape most navigation libraries need, and it costs one `when` that the compiler
 * checks the moment a route class changes.
 */
@Composable
fun VoyagerDemo(route: Any?) {
  val screens = remember(route) {
    when (val target = route.toScreen()) {
      null, VoyagerHome -> listOf(VoyagerHome)
      else -> listOf(VoyagerHome, target)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Navigator(screens = screens) { CurrentScreen() }
  }
}

/** The route-to-screen mapping. Perch hands back the route; picking the screen is the app's call. */
private fun Any?.toScreen(): Screen? = when (this) {
  is HomeLink -> VoyagerHome
  is PaymentLink -> VoyagerPaymentScreen(id)
  else -> null
}

// `data object` rather than `object`: Voyager's Screen is java.io.Serializable on Android, so a
// restored back stack holds a second instance of this. A data object's equals compares by type, so
// that instance still equals this one and `toScreen()`'s result still matches below.
private data object VoyagerHome : Screen {

  @Composable
  override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    DestinationBody(
      screen = "Home",
      detail = "The bottom of the Voyager stack.",
      note = "Reached from HomeLink, or as the screen this demo always starts on.",
    ) {
      Button(onClick = { navigator.push(VoyagerPaymentScreen("from-in-app")) }) {
        Text("Push a payment")
      }
    }
  }
}

private data class VoyagerPaymentScreen(val id: String) : Screen {

  @Composable
  override fun Content() {
    DestinationBody(
      screen = "Payment",
      detail = "id = $id",
      note = "toScreen() carried PaymentLink.id across; back pops to Home.",
    )
  }
}
