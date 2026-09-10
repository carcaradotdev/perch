package com.example.sample.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The whole sample app. One activity, one job: turn the URL Android handed it into a route object,
 * and hand that object to whichever navigation library the reader picked on the first screen.
 *
 * Perch's part is the single `parse` call in [SampleShell]. Everything below that is the three
 * navigators being themselves.
 */
class MainActivity : ComponentActivity() {

  /**
   * The URL of the intent that most recently arrived, or null on a plain launcher tap.
   *
   * A cold start reads it from `intent`; a link opened while the app is already running comes
   * through [onNewIntent] instead, which is why this is state rather than a value read once.
   */
  private var incoming by mutableStateOf<IncomingUrl?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Only on a fresh start. A recreated activity - a rotation, a font-size change - is handed the
    // same VIEW intent again, and reading it here unconditionally would look like the link had just
    // arrived a second time: the app would jump back to the picker and throw away whatever screen
    // the reader was on. `savedInstanceState` is what tells the two apart.
    if (savedInstanceState == null) {
      incoming = intent?.dataString?.let(::IncomingUrl)
    }

    setContent {
      MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
      ) {
        SampleShell(incoming)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    incoming = intent.dataString?.let(::IncomingUrl)
  }
}
