package com.example.sample.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.sample.app.SampleGraph
import com.example.sample.di.DeepLinkNavigator
import com.example.sample.di.Routing
import com.example.sample.di.SessionState
import com.example.sample.navigation.SampleRoute

/**
 * What the shell owes the graph, and the graph built from it.
 *
 * Both interfaces are implemented here because both are platform-shaped: navigation is Compose
 * state on this side and a `NavigationPath` on the other, and a session is whatever the app already
 * has.
 *
 * It is created by [SampleShell] rather than by the demo screen, which is the whole point of a
 * graph scoped to the application: `SampleIosApp` holds its `Shell` for the app's lifetime, and a
 * screen-scoped copy would hand the reader a singleton that is not one. Leaving the demo and coming
 * back keeps the session and the last routing.
 */
class DemoApp : DeepLinkNavigator, SessionState {

  override var signedIn by mutableStateOf(false)

  var routing by mutableStateOf<Routing?>(null)
    private set

  private val graph = SampleGraph.create(navigator = this, session = this)

  // A real shell pushes a screen here. This one records nothing of its own: the route the router
  // sent is the `destination` it reports back, and the readout takes it from there.
  override fun goTo(route: SampleRoute) = Unit

  fun open(url: String) {
    routing = graph.router.open(url)
  }
}
