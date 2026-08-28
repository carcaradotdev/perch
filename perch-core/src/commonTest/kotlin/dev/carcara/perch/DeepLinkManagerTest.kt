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
  private lateinit var tokenProvider: FakeDeepLinkAuthGate
  private lateinit var appLockManager: FakeDeepLinkLockGate

  @BeforeTest
  fun setUp() {
    // The manager launches on Dispatchers.Main; this module has no platform main
    // dispatcher in tests, so route it to the test scheduler.
    Dispatchers.setMain(UnconfinedTestDispatcher())
    navigator = RecordingDeepLinkNavigator()
    tokenProvider = FakeDeepLinkAuthGate()
    appLockManager = FakeDeepLinkLockGate()
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
      authGate = tokenProvider,
      lockGate = appLockManager,
      routeHandlers = handlers,
      scope = CoroutineScope(backgroundScope.coroutineContext + testDispatcher),
      handlerDispatcher = testDispatcher,
      logger = logger,
    ).also { if (navigationReady) it.setNavigationReady() }
  }

  @Test
  fun `auth-required route navigates immediately when token success and unlocked`() = runTest {
    tokenProvider.setSuccess()
    appLockManager.unlock()

    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `auth-required route is queued when token success but app is locked`() = runTest {
    tokenProvider.setSuccess()
    // AppLockManager defaults to locked.

    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.navigateCallCount)
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)
  }

  @Test
  fun `auth-required route is queued when token error even if unlocked`() = runTest {
    appLockManager.unlock()
    // TokenProvider defaults to no token.

    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.navigateCallCount)
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)
  }

  @Test
  fun `pending route navigates after token success and unlock both happen`() = runTest {
    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()
    assertEquals(0, navigator.navigateCallCount)

    tokenProvider.setSuccess()
    advanceUntilIdle()
    // Token is now valid but lock is still down, so it must still wait.
    assertEquals(0, navigator.navigateCallCount)
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)

    appLockManager.unlock()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `pending route navigates after unlock happens before token when token arrives second`() = runTest {
    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    appLockManager.unlock()
    advanceUntilIdle()
    assertEquals(0, navigator.navigateCallCount)

    tokenProvider.setSuccess()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
  }

  @Test
  fun `no-auth route navigates immediately even when locked and unauthenticated`() = runTest {
    val manager = createManager()
    manager.handleRoute(NoAuthRoute)
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
  }

  @Test
  fun `clearPendingRoute drops the queued deep link`() = runTest {
    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)

    manager.clearPendingRoute()
    tokenProvider.setSuccess()
    appLockManager.unlock()
    advanceUntilIdle()

    assertEquals(0, navigator.navigateCallCount)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `auth-required route is queued until host signals navigation ready`() = runTest {
    tokenProvider.setSuccess()
    appLockManager.unlock()

    val manager = createManager(navigationReady = false)
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.navigateCallCount)
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)

    manager.setNavigationReady()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `no-auth route also waits for navigation ready`() = runTest {
    val manager = createManager(navigationReady = false)
    manager.handleRoute(NoAuthRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.navigateCallCount)
    assertEquals(NoAuthRoute, manager.pendingRoute.value)

    manager.setNavigationReady()
    advanceUntilIdle()

    assertEquals(NoAuthRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
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
    assertEquals(0, navigator.navigateCallCount)
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

    assertEquals(NoAuthRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
    assertTrue(manager.bootstrapTakenOver.value)
  }

  @Test
  fun `handler cancel performs no navigation and leaves bootstrapTakenOver false`() = runTest {
    val handler = DeepLinkRouteHandler<HandlerRoute> { _ -> DeepLinkResolution.Cancel }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.navigateCallCount)
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

    assertEquals(0, navigator.navigateCallCount)
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
    assertEquals(0, navigator.navigateCallCount)

    release.complete(Unit)
    advanceUntilIdle()

    assertFalse(manager.isProcessingDeepLink.value)
    assertEquals(NoAuthRoute, navigator.lastNavigatedRoute)
  }

  @Test
  fun `dedupe — handleRoute with same route while pending is ignored`() = runTest {
    // No token, no unlock — gates closed, route stays pending.
    val manager = createManager()
    manager.handleRoute(AuthRequiredRoute)
    manager.handleRoute(AuthRequiredRoute)
    manager.handleRoute(AuthRequiredRoute)
    advanceUntilIdle()

    // Open gates once and assert single navigation.
    tokenProvider.setSuccess()
    appLockManager.unlock()
    advanceUntilIdle()

    assertEquals(1, navigator.navigateCallCount)
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
    assertEquals(1, navigator.navigateCallCount)
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
    assertEquals(SecondNoAuthRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
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
    // Token already valid but the app stays locked. Ingress (HandlerRoute) is
    // requiresAuth=false so the handler runs; the *resolved* target is AuthRequiredRoute,
    // which must wait on the lock just like a direct ingress would.
    tokenProvider.setSuccess()

    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      DeepLinkResolution.Resolved(AuthRequiredRoute, NavigationMode.Push)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    // Handler resolved but the resolved route is gated: nothing pushed yet.
    assertEquals(0, navigator.navigateCallCount)
    assertFalse(manager.bootstrapTakenOver.value)
    // isProcessingDeepLink must remain true through the post-handler wait.
    assertTrue(manager.isProcessingDeepLink.value)
    // Public pendingRoute view reflects the resolved-but-waiting target.
    assertEquals(AuthRequiredRoute, manager.pendingRoute.value)

    appLockManager.unlock()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastNavigatedRoute)
    assertEquals(1, navigator.navigateCallCount)
    assertTrue(manager.bootstrapTakenOver.value)
    assertFalse(manager.isProcessingDeepLink.value)
    assertNull(manager.pendingRoute.value)
  }

  @Test
  fun `handler-resolved route reaching auth-required target waits for token success`() = runTest {
    // App already unlocked but no auth token yet. Symmetric to the lock case.
    appLockManager.unlock()

    val handler = DeepLinkRouteHandler<HandlerRoute> { _ ->
      DeepLinkResolution.Resolved(AuthRequiredRoute, NavigationMode.SetRoot)
    }
    val manager = createManager(handlers = mapOf(HandlerRoute::class to handler))

    manager.handleRoute(HandlerRoute)
    advanceUntilIdle()

    assertEquals(0, navigator.setRootCallCount)
    assertFalse(manager.bootstrapTakenOver.value)
    assertTrue(manager.isProcessingDeepLink.value)

    tokenProvider.setSuccess()
    advanceUntilIdle()

    assertEquals(AuthRequiredRoute, navigator.lastSetRootRoute)
    assertEquals(1, navigator.setRootCallCount)
    assertTrue(manager.bootstrapTakenOver.value)
    assertFalse(manager.isProcessingDeepLink.value)
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
