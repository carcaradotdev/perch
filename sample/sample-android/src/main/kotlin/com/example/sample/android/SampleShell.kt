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
import com.example.sample.app.sampleParser

/** The URL the picker opens with, so the app is useful without reaching for `adb`. */
private const val DEFAULT_URL = "sample://payments/abc123"

/**
 * One arrival of one URL, carried by identity rather than by value: firing the same deep link a
 * second time is a second arrival, and the app should react to it again.
 */
class IncomingUrl(val url: String)

/**
 * The demos, in the order the picker lists them.
 *
 * Both navigators here are Kotlin Multiplatform. That is the bar for being in this sample: Perch
 * parses one URL into one route object for every target, so a navigator that only ships an Android
 * artifact has nothing to say about that.
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
}

/**
 * The picker, and whichever demo is open on top of it.
 *
 * The `parse` call below is Perch's entire part in this app. What comes back is a route object of
 * sample-routes' own types, or null; from there the three demos differ only in what they do with
 * that object.
 */
@Composable
fun SampleShell(incoming: IncomingUrl?) {
  val parser = remember { sampleParser() }
  var url by rememberSaveable { mutableStateOf(DEFAULT_URL) }
  var demo by rememberSaveable { mutableStateOf<Demo?>(null) }

  // A link that arrives while the app is running takes over the box and returns to the picker, so
  // the reader can choose which navigator they want to watch receive it.
  LaunchedEffect(incoming) {
    if (incoming != null) {
      url = incoming.url
      demo = null
    }
  }

  val route = remember(url) { parser.parse(url) }

  // Back leaves the open demo rather than the app. Each demo composes its own back handling after
  // this one, so its internal navigation gets first refusal and this only fires once that demo's
  // own stack is down to a single screen.
  BackHandler(enabled = demo != null) { demo = null }

  when (val open = demo) {
    null -> DemoPicker(url = url, route = route, onUrlChange = { url = it }, onPick = { demo = it })
    else -> OpenDemo(demo = open, route = route, onBack = { demo = null })
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
private fun OpenDemo(demo: Demo, route: Any?, onBack: () -> Unit) {
  Column(modifier = Modifier.fillMaxSize().safeContentPadding()) {
    ScreenHeader(title = demo.title, onBack = onBack)
    when (demo) {
      Demo.Nav3 -> Nav3Demo(route)
      Demo.Voyager -> VoyagerDemo(route)
    }
  }
}
