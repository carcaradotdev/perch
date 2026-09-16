package com.example.sample.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.sample.di.Routing
import com.example.sample.di.SessionState
import com.example.sample.home.api.HomeDeepLink
import com.example.sample.payments.api.PaymentRoutes

/**
 * The same link, through a graph instead of through a `parse` call.
 *
 * This demo is handed the URL rather than a route, which is the difference worth looking at: the
 * parser is a binding inside the graph, so nothing on this screen names it, and adding a feature to
 * the app would change where the button below lands without changing a line of it.
 *
 * Nothing here pushes a screen either. The button asks the router to open a URL and the router
 * calls back into [DemoApp.goTo], which is what moves the stack — so what the reader watches is a
 * deep link driving navigation from inside the graph, not a screen reacting to a parse result.
 *
 * The switch is what makes the rest visible. `sample://payment-approvals` resolves to approvals
 * either way; where it lands depends on a gate that lives in the payments module and reads a
 * [SessionState] the shell supplied once, at graph creation.
 */
@Composable
fun DiDemo(app: DemoApp, url: String) {
  if (app.backStack.isEmpty()) {
    Controles(app, url)
    return
  }

  // Back pops the router's stack before the shell's own handler gets to close the demo.
  BackHandler { app.back() }

  NavDisplay(
    backStack = app.backStack,
    modifier = Modifier.fillMaxSize(),
    onBack = { app.back() },
    entryProvider = entryProvider<NavKey> {
      entry<HomeDeepLink> {
        Destino("Home", "The link resolved here.", app)
      }
      entry<PaymentRoutes.Approvals> {
        Destino("Approvals", "The payments waiting on someone.", app)
      }
      entry<PaymentRoutes.Details> { key ->
        Destino("Payment", "id = ${key.id}", app)
      }
    },
  )
}

/**
 * A destination, with the sentence that explains how the reader got to it.
 *
 * The routing result is read here rather than on the panel because this is where the surprise is:
 * ask for approvals signed out and this says Home at the top while still naming approvals as what
 * the URL meant.
 */
@Composable
private fun Destino(titulo: String, detalhe: String, app: DemoApp) {
  val routing = app.routing
  DestinationBody(
    screen = titulo,
    detail = detalhe,
    // What the router reports is which route it pushed, not whether a handler ran - it hands the
    // map's answer on without recording that it asked. So this says what happened and stops there:
    // an unchanged route means either no handler for it, or a handler that let it through, and
    // nothing on this screen can tell those apart.
    note = when {
      routing !is Routing.Navigated -> "Opened through the router."
      routing.matched == routing.destination ->
        "The router pushed the route the URL named."
      else ->
        "The URL named ${describeRoute(routing.matched)}. A handler in the payments module " +
          "sent it here instead, and the router pushed what the handler returned."
    },
  ) {
    Button(onClick = { app.back() }) { Text("Back to the panel") }
  }
}

@Composable
private fun Controles(app: DemoApp, url: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("URL", style = MaterialTheme.typography.labelMedium)
    Text(url, style = MaterialTheme.typography.bodyLarge)

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Switch(checked = app.signedIn, onCheckedChange = { app.signedIn = it })
      Text(if (app.signedIn) "Signed in" else "Signed out")
    }

    Button(onClick = { app.open(url) }, modifier = Modifier.fillMaxWidth()) {
      Text("Open through the router")
    }

    when (val routing = app.routing) {
      null -> Text(
        "Nothing opened yet. The router navigates; this panel only asks it to.",
        style = MaterialTheme.typography.bodyMedium,
      )

      Routing.Unmatched -> Text(
        "No feature claims that URL, so the router navigated nowhere and you are still here.",
        style = MaterialTheme.typography.bodyMedium,
      )

      is Routing.Navigated -> RouteReadout(label = "Last sent to the navigator", route = routing.destination)
    }
  }
}
