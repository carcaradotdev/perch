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
    incoming = intent?.dataString?.let(::IncomingUrl)

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
