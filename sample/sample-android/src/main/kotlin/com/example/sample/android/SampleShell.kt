package com.example.sample.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sample.app.SampleLinks
import com.example.sample.app.sampleParser

/**
 * The demos, in the order the picker lists them.
 *
 * Both navigators here are Kotlin Multiplatform. That is the bar for being in this sample: Perch
 * parses one URL into one route object for every target, so a navigator that only ships an Android
 * artifact has nothing to say about that. The third demo is not a navigator at all - it is the
 * same parse reached through a dependency graph.
 */
private enum class Demo(val title: String, val summary: String) {
  Nav3(
    title = "Navigation 3",
    summary = "Its back stack holds NavKey, and these routes are NavKeys. Nothing to adapt.",
  ),
  Voyager(
    title = "Voyager",
    summary = "A Screen is UI, so the route maps to one here, where the UI already lives.",
  ),
  Di(
    title = "Dependency injection",
    summary = "The parser is a binding. The screen hands over a URL and never names it.",
  ),
}

/**
 * The picker, and whichever demo is open on top of it.
 *
 * The `parse` call below is Perch's entire part in this screen. What comes back is one of the
 * features' own route types, or null; from there the demos differ only in what they do with it -
 * except the last, which is handed the URL and does its own parsing inside a graph.
 */
@Composable
fun SampleShell(incoming: IncomingUrl?) {
  val parser = remember { sampleParser() }
  // Built here, not in the demo that uses it: the graph is scoped to the application, and this is
  // the composable that lasts as long as the app does.
  val app = remember { DemoApp() }
  var url by rememberSaveable { mutableStateOf(SampleLinks.DEFAULT) }
  var demo by rememberSaveable { mutableStateOf<Demo?>(null) }

  LaunchedEffect(incoming) {
    if (incoming != null) {
      url = incoming.url

      // Every link the OS delivers goes through the graph, not only the ones the DI demo's button
      // asks about. Without this line the router would be a thing a button calls, and the path a
      // deep link actually takes on Android would be the one part of the sample nothing exercises.
      app.open(incoming.url)

      // The two navigator demos are handed a route somebody else parsed, so a link arriving while
      // one of them is open returns to the picker and lets the reader choose which receives it.
      // The DI demo did the routing itself, so it keeps the screen: the reader is already where
      // the link sent them, which is what a deep link is supposed to do.
      if (demo != Demo.Di) demo = null
    }
  }

  val route = remember(url) { parser.parse(url) }

  // Back leaves the open demo rather than the app. Each demo composes its own back handling after
  // this one, so its internal navigation gets first refusal and this only fires once that demo's
  // own stack is down to a single screen.
  BackHandler(enabled = demo != null) { demo = null }

  when (val open = demo) {
    null -> DemoPicker(url = url, route = route, onUrlChange = { url = it }, onPick = { demo = it })
    else -> OpenDemo(demo = open, app = app, url = url, route = route, onBack = { demo = null })
  }
}

@Composable
private fun DemoPicker(
  url: String,
  route: Any?,
  onUrlChange: (String) -> Unit,
  onPick: (Demo) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text("Perch", style = MaterialTheme.typography.headlineMedium)
    Text(
      "A URL in, a route object out. Pick a navigator to watch that object land.",
      style = MaterialTheme.typography.bodyMedium,
    )

    OutlinedTextField(
      value = url,
      onValueChange = onUrlChange,
      label = { Text("Deep link") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    RouteReadout(label = "parse() returned", route = route)

    Demo.entries.forEach { demo ->
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Button(onClick = { onPick(demo) }, modifier = Modifier.fillMaxWidth()) {
          Text(demo.title)
        }
        Text(demo.summary, style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun OpenDemo(demo: Demo, app: DemoApp, url: String, route: Any?, onBack: () -> Unit) {
  Column(modifier = Modifier.fillMaxSize().safeContentPadding()) {
    ScreenHeader(title = demo.title, onBack = onBack)
    when (demo) {
      Demo.Nav3 -> Nav3Demo(route)
      Demo.Voyager -> VoyagerDemo(route)
      // The URL, not the route: this demo's parser lives in the graph, so parsing is its job.
      Demo.Di -> DiDemo(app, url)
    }
  }
}
