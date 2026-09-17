package com.example.sample.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.example.sample.app.SampleGraph
import com.example.sample.di.DeepLinkNavigator
import com.example.sample.di.Routing
import com.example.sample.di.SessionState
import com.example.sample.navigation.SampleRoute

/**
 * What the shell owes the graph, and the graph built from it.
 *
 * Both interfaces are implemented here because both are platform-shaped: navigation is a Compose
 * back stack on this side and a `NavigationPath` on the other, and a session is whatever the app
 * already has.
 *
 * It is created by [SampleShell] rather than by the demo screen, which is the whole point of a
 * graph scoped to the application: `SampleIosApp` holds its `Shell` for the app's lifetime, and a
 * screen-scoped copy would hand the reader a singleton that is not one. Leaving the demo and coming
 * back keeps the session, the stack and the last routing.
 */
class DemoApp : DeepLinkNavigator, SessionState {

  override var signedIn by mutableStateOf(false)

  /** Where the router has sent the reader. Empty is the control panel. */
  val backStack: MutableList<NavKey> = mutableStateListOf()

  var routing by mutableStateOf<Routing?>(null)
    private set

  private val graph = SampleGraph.create(navigator = this, session = this)

  /**
   * The seam earning its place: the router decided, and this is the app doing it.
   *
   * There is nothing to adapt. A route reaches here as the object `parse` returned, and Navigation
   * 3's back stack holds `NavKey`s, which these routes are — so the push is an `add`. Whether the
   * route on this line is the one the URL named is not something this can tell, and not something
   * it should: a handler in the payments module may have replaced it on the way.
   */
  override fun goTo(route: SampleRoute) {
    backStack.add(route)
  }

  fun back() {
    backStack.removeLastOrNull()
  }

  fun open(url: String) {
    routing = graph.router.open(url)
  }
}
