package com.example.sample.app

import com.example.sample.di.DeepLinkNavigator
import com.example.sample.di.Routing
import com.example.sample.di.SessionState
import com.example.sample.home.api.HomeDeepLink
import com.example.sample.navigation.SampleRoute
import com.example.sample.payments.api.PaymentRoutes
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The graph, assembled and run.
 *
 * Every test here goes through `sampleGraph`, so what they cover is the wiring rather than the
 * router in isolation: the parser provider, the handler map, and a handler reaching a dependency
 * the shell supplied.
 */
class SampleGraphTest {

  private class RecordingNavigator : DeepLinkNavigator {
    val visited = mutableListOf<SampleRoute>()
    override fun goTo(route: SampleRoute) {
      visited += route
    }
  }

  private fun route(url: String, signedIn: Boolean = true): Pair<Routing, RecordingNavigator> {
    val navigator = RecordingNavigator()
    val session = object : SessionState {
      override val signedIn: Boolean = signedIn
    }
    return SampleGraph.create(navigator, session).router.open(url) to navigator
  }

  @Test
  fun `a link with no handler navigates to the route it parsed to`() {
    val (routing, navigator) = route("sample://payments/abc123")

    assertEquals(Routing.Navigated(PaymentRoutes.Details("abc123"), PaymentRoutes.Details("abc123")), routing)
    assertEquals(listOf<SampleRoute>(PaymentRoutes.Details("abc123")), navigator.visited)
  }

  @Test
  fun `the payments handler sends a signed-out visitor home`() {
    val (routing, navigator) = route("sample://payment-approvals", signedIn = false)

    assertEquals(Routing.Navigated(PaymentRoutes.Approvals, HomeDeepLink), routing)
    assertEquals(listOf<SampleRoute>(HomeDeepLink), navigator.visited)
  }

  @Test
  fun `the same link reaches approvals once someone is signed in`() {
    val (routing, _) = route("sample://payment-approvals", signedIn = true)

    assertEquals(Routing.Navigated(PaymentRoutes.Approvals, PaymentRoutes.Approvals), routing)
  }

  @Test
  fun `a URL no feature claims navigates nowhere`() {
    val (routing, navigator) = route("sample://nothing-matches-this")

    assertEquals(Routing.Unmatched, routing)
    assertEquals(emptyList<SampleRoute>(), navigator.visited)
  }
}
