package com.example.sample.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.sample.di.Routing
import com.example.sample.di.SessionState

/**
 * The same link, through a graph instead of through a `parse` call.
 *
 * This demo is handed the URL rather than a route, which is the difference worth looking at: the
 * parser is a binding inside the graph, so nothing on this screen names it, and adding a feature
 * to the app would change what the button below does without changing a line of it.
 *
 * The switch is what makes the rest visible. `sample://payment-approvals` resolves to approvals
 * either way; where it lands depends on a gate that lives in the payments module and reads a
 * [SessionState] the shell supplied once, at graph creation.
 */
@Composable
fun DiDemo(app: DemoApp, url: String) {
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

    Outcome(app.routing)
  }
}

@Composable
private fun Outcome(routing: Routing?) {
  when (routing) {
    null -> Text("Nothing opened yet.", style = MaterialTheme.typography.bodyMedium)

    Routing.Unmatched -> Text(
      "No feature claims this URL, so the router navigated nowhere.",
      style = MaterialTheme.typography.bodyMedium,
    )

    is Routing.Navigated -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      RouteReadout(label = "The URL resolved to", route = routing.matched)
      RouteReadout(label = "The navigator was sent", route = routing.destination)
      Text(
        if (routing.matched == routing.destination) {
          "No handler is registered for this route, so it went where it parsed."
        } else {
          "A handler in the payments module redirected it. The router read a map it was handed " +
            "and never learned which module filled it."
        },
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}
