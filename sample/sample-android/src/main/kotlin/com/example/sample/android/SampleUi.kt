/*
 * Copyright 2026 Carcara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import com.example.sample.home.api.HomeDeepLink
import com.example.sample.payments.api.PaymentRoutes

/**
 * How a parsed route reads on screen.
 *
 * Routes are plain classes with no `toString`, which is the point: Perch hands back an object of
 * the app's own type, and rendering it is the app's business.
 */
fun describeRoute(route: Any?): String = when (route) {
  is HomeDeepLink -> "HomeDeepLink"
  is PaymentRoutes.Details -> "PaymentRoutes.Details(id = ${route.id})"
  is PaymentRoutes.Approvals -> "PaymentRoutes.Approvals"
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

/** A labelled one-line rendering of a route, used by the picker and by both demos. */
@Composable
fun RouteReadout(label: String, route: Any?) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
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
