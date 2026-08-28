package dev.carcara.perch

import dev.carcara.perch.test.FakeDeepLinkAuthGate
import dev.carcara.perch.test.FakeDeepLinkLockGate
import dev.carcara.perch.test.RecordingDeepLinkNavigator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkManagerTest {

  private lateinit var navigator: RecordingDeepLinkNavigator
  private lateinit var authGate: FakeDeepLinkAuthGate
  private lateinit var lockGate: FakeDeepLinkLockGate

  @BeforeTest
  fun setUp() {
    // The manager launches on Dispatchers.Main; this module has no platform main
    // dispatcher in tests, so route it to the test scheduler.
    Dispatchers.setMain(UnconfinedTestDispatcher())
    navigator = RecordingDeepLinkNavigator()
    authGate = FakeDeepLinkAuthGate()
    lockGate = FakeDeepLinkLockGate()
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun TestScope.createManager(
    navigationReady: Boolean = true,
    handlers: Map<KClass<out DeepLinkTarget>, DeepLinkRouteHandler<*>> = emptyMap(),
    logger: DeepLinkLogger = DeepLinkLogger.None,
  ): DeepLinkManager {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    return DeepLinkManager(
      navigator = navigator,
      parser = DeepLinkParser(schemes = setOf("acme")),
      authGate = authGate,
      lockGate = lockGate,
      routeHandlers = handlers,
      scope = CoroutineScope(backgroundScope.coroutineContext + testDispatcher),
      handlerDispatcher = testDispatcher,
      logger = logger,
    ).also { if (navigationReady) it.setNavigationReady() }
  }

  @Test
  fun `auth-required route navigates immediately when authenticated and unlocked`() = runTest {
    authGate.authenticate()
    lockGate.unlock()

    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `auth-required route is queued when authenticated but the app is locked`() = runTest {
    authGate.authenticate()
    // The lock gate defaults to locked.

    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.pushCallCount)
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)
  }

  @Test
  fun `auth-required route is queued when unauthenticated even if unlocked`() = runTest {
    lockGate.unlock()
    // The auth gate defaults to signed out.

    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.pushCallCount)
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)
  }

  @Test
  fun `pending route navigates after authentication and unlock both happen`() = runTest {
    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()
    assertEquals(0, navigator.pushCallCount)

    authGate.authenticate()
    advanceUntilIdle()
    // Authenticated now, but the lock is still down, so it must still wait.
    assertEquals(0, navigator.pushCallCount)
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)

    lockGate.unlock()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `pending route navigates when unlock happens first and authentication second`() = runTest {
    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    lockGate.unlock()
    advanceUntilIdle()
    assertEquals(0, navigator.pushCallCount)

    authGate.authenticate()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
  }

  @Test
  fun `no-auth route navigates immediately even when locked and unauthenticated`() = runTest {
    val manager = createManager()
    manager.handleRoute(NoAuthRoute)
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
  }

  @Test
  fun `clearPendingRoute drops the queued deep link`() = runTest {
    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)

    manager.clearPendingRoute()
    authGate.authenticate()
    lockGate.unlock()
    advanceUntilIdle()

    assertEquals(0, navigator.pushCallCount)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `auth-required route is queued until host signals navigation ready`() = runTest {
    authGate.authenticate()
    lockGate.unlock()

    val manager = createManager(navigationReady = false)
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.pushCallCount)
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)

    manager.setNavigationReady()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `no-auth route also waits for navigation ready`() = runTest {
    val manager = createManager(navigationReady = false)
    manager.handleRoute(NoAuthRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.pushCallCount)
    assertEquals(NoAuthRoute, manager.pendingRoute.value)

    manager.setNavigationReady()
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
  }

  // --- Custom route handler tests ---

  @Test
  fun `handler resolution navigates to resolved route with chosen mode and flips bootstrapTakenOver`() = runTest {
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      DeepLinkResolution.Resolved(NoAuthRoute, NavigationMode.SetRoot)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastSetRootRoute)
    assertEquals(1, navigator.setRootCallCount)
    assertEquals(0, navigator.pushCallCount)
    assertFalse(manager.isProcessingDeepLink.value)
    assertTrue(manager.bootstrapTakenOver.value)
  }

  @Test
  fun `handler fallback navigates to fallback route and flips bootstrapTakenOver`() = runTest {
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      DeepLinkResolution.Fallback(NoAuthRoute, NavigationMode.Push)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
    assertTrue(manager.bootstrapTakenOver.value)
  }

  @Test
  fun `handler cancel performs no navigation and leaves bootstrapTakenOver false`() = runTest {
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ -> DeepLinkResolution.Cancel }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.pushCallCount)
    assertEquals(0, navigator.setRootCallCount)
    assertFalse(manager.bootstrapTakenOver.value)
    assertFalse(manager.isProcessingDeepLink.value)
  }

  @Test
  fun `handler thrown error treated as Cancel`() = runTest {
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ -> error("boom") }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.pushCallCount)
    assertFalse(manager.bootstrapTakenOver.value)
    assertFalse(manager.isProcessingDeepLink.value)
  }

  @Test
  fun `handler thrown error is reported to the logger with the thrown exception`() = runTest {
    val logger = RecordingLogger()
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ -> throw IllegalStateException("boom") }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler), logger = logger)

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    // kotlinx.coroutines' JVM stack-trace recovery can rethrow a copy of the original
    // exception (same type and message, different identity), so this checks content
    // rather than reference identity.
    assertEquals(1, logger.errors.size)
    val call = logger.errors.single()
    assertEquals("Deep link handler failed for HandlerRoute", call.message)
    assertEquals("boom", call.throwable?.message)
    assertTrue(call.throwable is IllegalStateException)
  }

  @Test
  fun `isProcessingDeepLink is true while handler is suspended`() = runTest {
    val release = CompletableDeferred<Unit>()
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      release.await()
      DeepLinkResolution.Resolved(NoAuthRoute)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()
    assertTrue(manager.isProcessingDeepLink.value)
    assertEquals(0, navigator.pushCallCount)

    release.complete(Unit)
    advanceUntilIdle()

    assertFalse(manager.isProcessingDeepLink.value)
    assertEquals(NoAuthRoute, navigator.lastPushedRoute)
  }

  @Test
  fun `dedupe — handleRoute with same route while pending is ignored`() = runTest {
    // Not authenticated, not unlocked — gates closed, route stays pending.
    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    manager.handleRoute(AuthRequiredRoute)
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    // Open gates once and assert single navigation.
    authGate.authenticate()
    lockGate.unlock()
    advanceUntilIdle()

    assertEquals(1, navigator.pushCallCount)
  }

  @Test
  fun `dedupe — handleRoute with same route while handler is processing is ignored`() = runTest {
    val release = CompletableDeferred<Unit>()
    var handlerInvocations = 0
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      handlerInvocations++
      release.await()
      DeepLinkResolution.Resolved(NoAuthRoute)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()
    assertTrue(manager.isProcessingDeepLink.value)

    // Triple-click: same route while in-flight — must be ignored.
    manager.handleRoute(HandlerRoute)
    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    release.complete(Unit)
    advanceUntilIdle()

    assertEquals(1, handlerInvocations)
    assertEquals(1, navigator.pushCallCount)
  }

  @Test
  fun `different route while handler is processing cancels in-flight job and starts new one`() = runTest {
    val firstRelease = CompletableDeferred<Unit>()
    var firstResolved = false
    val firstHandler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      firstRelease.await()
      firstResolved = true
      DeepLinkResolution.Resolved(NoAuthRoute)
    }
    val secondHandler = DeepLinkRouteHandler<OtherHandlerRoute> { _ ->
      DeepLinkResolution.Resolved(SecondNoAuthRoute)
    }
    val manager = createManager(
      handlers = mapOf(
        HandlerRoute::class to firstHandler,
        OtherHandlerRoute::class to secondHandler,
      ),
    )

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()
    assertTrue(manager.isProcessingDeepLink.value)

    manager.handleRoute(OtherHandlerRoute)
    advanceUntilIdle()

    // First handler was cancelled before completing; second handler ran to completion.
    assertFalse(firstResolved)
    assertEquals(SecondNoAuthRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
  }

  @Test
  fun `handler Present mode invokes navigator present`() = runTest {
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      DeepLinkResolution.Resolved(NoAuthRoute, NavigationMode.Present)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastPresentedRoute)
    assertEquals(1, navigator.presentCallCount)
  }

  @Test
  fun `handler-resolved auth-required route waits for unlock before navigating`() = runTest {
    // Already authenticated but the app stays locked. Ingress (HandlerRoute) is
    // requiresAuth=false so the handler runs; the *resolved* target is AuthRequiredRoute,
    // which must wait on the lock just like a direct ingress would.
    authGate.authenticate()

    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      DeepLinkResolution.Resolved(AuthRequiredRoute, NavigationMode.Push)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    // Handler resolved but the resolved route is gated: nothing pushed yet.
    assertEquals(0, navigator.pushCallCount)
    assertFalse(manager.bootstrapTakenOver.value)
    // isProcessingDeepLink must remain true through the post-handler wait.
    assertTrue(manager.isProcessingDeepLink.value)
    // Public pendingRoute view reflects the resolved-but-waiting target.
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)

    lockGate.unlock()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
    assertTrue(manager.bootstrapTakenOver.value)
    assertFalse(manager.isProcessingDeepLink.value)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `handler-resolved route reaching auth-required target waits for authentication`() = runTest {
    // App already unlocked but not yet authenticated. Symmetric to the lock case.
    lockGate.unlock()

    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      DeepLinkResolution.Resolved(AuthRequiredRoute, NavigationMode.SetRoot)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.setRootCallCount)
    assertFalse(manager.bootstrapTakenOver.value)
    assertTrue(manager.isProcessingDeepLink.value)

    authGate.authenticate()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastSetRootRoute)
    assertEquals(1, navigator.setRootCallCount)
    assertTrue(manager.bootstrapTakenOver.value)
    assertFalse(manager.isProcessingDeepLink.value)
  }

  // --- Same-type deep links replace the top of the stack rather than duplicating it ---

  @Test
  fun `deep link to the target type already on top replaces instead of pushing`() = runTest {
    navigator.currentTarget = NoAuthRoute

    val manager = createManager()
    manager.handleRoute(NoAuthRoute)
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastReplacedRoute)
    assertEquals(1, navigator.replaceCallCount)
    assertEquals(0, navigator.pushCallCount)
  }

  @Test
  fun `deep link to a different target type than the one on top pushes`() = runTest {
    navigator.currentTarget = SecondNoAuthRoute

    val manager = createManager()
    manager.handleRoute(NoAuthRoute)
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
    assertEquals(0, navigator.replaceCallCount)
  }

  @Test
  fun `handler Replace mode invokes navigator replace`() = runTest {
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      DeepLinkResolution.Resolved(NoAuthRoute, NavigationMode.Replace)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastReplacedRoute)
    assertEquals(1, navigator.replaceCallCount)
    assertEquals(0, navigator.pushCallCount)
  }
}

private object AuthRequiredRoute : DeepLinkTarget {
  override val requiresAuth: Boolean = true
}

private object NoAuthRoute : DeepLinkTarget {
  override val requiresAuth: Boolean = false
}

private object SecondNoAuthRoute : DeepLinkTarget {
  override val requiresAuth: Boolean = false
}

private object HandlerRoute : DeepLinkTarget {
  override val requiresAuth: Boolean = false
}

private object OtherHandlerRoute : DeepLinkTarget {
  override val requiresAuth: Boolean = false
}

private class RecordingLogger : DeepLinkLogger {
  data class ErrorCall(val message: String, val throwable: Throwable?)

  val errors: MutableList<ErrorCall> = mutableListOf()

  override fun error(message: String, throwable: Throwable?) {
    errors += ErrorCall(message, throwable)
  }
}
