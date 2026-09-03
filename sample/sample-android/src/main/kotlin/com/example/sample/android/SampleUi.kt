package com.example.sample.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sample.routes.HomeLink
import com.example.sample.routes.PaymentLink

/**
 * How a parsed route reads on screen.
 *
 * Routes are plain classes with no `toString`, which is the point: Perch hands back an object of
 * the app's own type, and rendering it is the app's business.
 */
fun describeRoute(route: Any?): String = when (route) {
  is HomeLink -> "HomeLink"
  is PaymentLink -> "PaymentLink(id = ${route.id})"
  null -> "null — no route matched this URL"
  else -> route::class.simpleName ?: "unknown route"
}

/** The title bar every demo screen wears, with the back affordance the shell listens for. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(onClick = onBack) { Text("Back") }
    Text(title, style = MaterialTheme.typography.titleMedium)
  }
}

/** A labelled one-line rendering of a route, used by the picker and by all three demos. */
@Composable
fun RouteReadout(label: String, route: Any?, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(label, style = MaterialTheme.typography.labelMedium)
      Text(describeRoute(route), style = MaterialTheme.typography.bodyLarge)
    }
  }
}

/** The body of a destination screen: what it is, and what it was told. */
@Composable
fun DestinationBody(
  screen: String,
  detail: String,
  note: String,
  content: @Composable () -> Unit = {},
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(screen, style = MaterialTheme.typography.headlineSmall)
    Text(detail, style = MaterialTheme.typography.bodyLarge)
    Text(note, style = MaterialTheme.typography.bodySmall)
    content()
  }
}
