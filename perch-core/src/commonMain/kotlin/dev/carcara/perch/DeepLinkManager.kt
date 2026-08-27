package dev.carcara.perch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

/**
 * Read-only view of [DeepLinkManager]'s deep-link processing state.
 *
 * Exposed separately from [DeepLinkManager] so a consumer's cold-start logic can depend on this
 * narrow surface instead of the full constructor.
 */
public interface DeepLinkBootstrapState {
  /**
   * True while a [DeepLinkRouteHandler] is running, or while a handler-resolved target is
   * still waiting on the auth, lock or navigation-ready gate.
   *
   * A consumer that shows a loading screen on cold start should keep it up for as long as
   * this stays true, so the covered window spans both the handler call and any post-handler
   * gate wait.
   */
  public val isProcessingDeepLink: StateFlow<Boolean>

  /**
   * True once [DeepLinkManager] has navigated for a handler-driven deep link.
   *
   * A consumer that also sets an initial destination on cold start must not do so once this
   * flips true, or it will overwrite the deep-link destination.
   */
  public val bootstrapTakenOver: StateFlow<Boolean>
}

/**
 * Manages deep link navigation with authentication awareness.
 *
 * Stores pending deep links when the user is not yet ready to navigate
 * (auth gate closed, lock gate closed, or host navigation stack not yet
 * built) and automatically navigates once all gates are open. The host
 * (iOS app, Android activity) signals readiness via [setNavigationReady]
 * once its root navigation stack has been mounted, so we never push onto
 * a half-initialised navigation controller.
 *
 * The caller owns the instance and the `scope` passed to its constructor; construct one
 * instance per app. Every coroutine here runs on [Dispatchers.Main] because [DeepLinkNavigator]
 * calls end up touching UIKit / Compose Nav3, both of which require the main thread.
 *
 * ## Custom route handlers
 *
 * Features can register a [DeepLinkRouteHandler] keyed by [DeepLinkTarget] subtype to do
 * `suspend` work (state fetch, eligibility checks) before the user lands on the
 * deep-link target. While the handler runs:
 *
 * - **Cold start**: [isProcessingDeepLink] is true; the app's startup presenter
 *   keeps its loading state, so the existing splash stays visible.
 * - **Warm start**: handler runs silently in the background; user stays on
 *   their current screen until the handler resolves.
 *
 * The handler's resolved target is fed back through the same auth/lock/nav-ready
 * gate the original ingress target went through — so a handler that suspends
 * long enough for the lock screen to re-appear, or whose resolved target has a
 * different `requiresAuth` than the incoming one, still honours the gate.
 * [isProcessingDeepLink] stays true through that second wait too, so the splash
 * does not flash through to the wrong destination.
 *
 * Once the manager actually performs navigation for a handler-driven deep link,
 * [bootstrapTakenOver] flips true so the startup presenter does not race-overwrite the
 * deep-link destination with its own setRoot call.
 */
public class DeepLinkManager public constructor(
  private val navigator: DeepLinkNavigator,
  private val parser: DeepLinkParser,
  private val scope: CoroutineScope,
  private val authGate: DeepLinkAuthGate = DeepLinkAuthGate.AlwaysAuthenticated,
  private val lockGate: DeepLinkLockGate = DeepLinkLockGate.AlwaysUnlocked,
  private val routeHandlers: Map<KClass<out DeepLinkTarget>, DeepLinkRouteHandler<*>> = emptyMap(),
  private val handlerDispatcher: CoroutineDispatcher = Dispatchers.Default,
  private val logger: DeepLinkLogger = DeepLinkLogger.None,
) : DeepLinkBootstrapState {

  // Pending entry waiting for the gates to open. Carries an optional
  // NavigationMode: null on first ingress (handler not yet run), non-null
  // after a handler has resolved, in which case [dispatch] skips the handler
  // lookup and goes straight to navigation. The wrapper is internal; the
  // public [pendingRoute] view exposes only the target, keeping mode tracking
  // an implementation detail.
  private val _pendingRoute = MutableStateFlow<PendingRoute?>(null)

  /**
   * The deep-link target queued for navigation, waiting on the auth, lock or
   * navigation-ready gate.
   *
   * Null once [DeepLinkNavigator] has been given the target, or after [clearPendingRoute]
   * drops it.
   */
  public val pendingRoute: StateFlow<DeepLinkTarget?> = _pendingRoute
    .map { it?.route }
    .flowOn(Dispatchers.Main)
    .stateIn(scope, SharingStarted.Eagerly, null)

  private val _isNavigationReady = MutableStateFlow(false)

  // True only during the inner suspend window of a [DeepLinkRouteHandler].
  // The public [isProcessingDeepLink] also extends to handler-resolved targets
  // still parked in [_pendingRoute] awaiting gates, so the splash gating
  // covers the full deeplink-processing window, not just the handler call.
  private val _handlerRunning = MutableStateFlow(false)
  override val isProcessingDeepLink: StateFlow<Boolean> =
    combine(_handlerRunning, _pendingRoute) { running, pending ->
      running || pending?.mode != null
    }.flowOn(Dispatchers.Main).stateIn(scope, SharingStarted.Eagerly, false)

  private val _bootstrapTakenOver = MutableStateFlow(false)
  override val bootstrapTakenOver: StateFlow<Boolean> = _bootstrapTakenOver.asStateFlow()

  private var processingJob: Job? = null
  private var processingRoute: DeepLinkTarget? = null

  init {
    observeAndNavigatePendingRoute()
  }

  private fun observeAndNavigatePendingRoute() {
    scope.launch(Dispatchers.Main) {
      combine(
        _pendingRoute,
        authGate.isAuthenticated,
        lockGate.isUnlocked,
        _isNavigationReady,
      ) { pending, isAuthenticated, isUnlocked, isNavReady ->
        DeepLinkGateState(
          pending = pending,
          isAuthenticated = isAuthenticated,
          isUnlocked = isUnlocked,
          isNavigationReady = isNavReady,
        )
      }
        .onEach { state ->
          if (state.pending != null && canNavigate(state)) {
            val pending = state.pending
            _pendingRoute.value = null
            dispatch(pending)
          }
        }
        .collect()
    }
  }

  /**
   * @return true if the URL was parsed and handled, false if the URL couldn't be parsed
   */
  public fun handleDeepLink(url: String): Boolean {
    val route = parser.parse(url) ?: return false
    handleRoute(route)
    return true
  }

  /**
   * Enqueues [route] for navigation. The Main-dispatched collector in
   * [observeAndNavigatePendingRoute] drains it as soon as all gates are
   * open, so [DeepLinkNavigator] calls always run on the UI thread regardless
   * of which thread calls this method.
   *
   * Dedupes against in-flight work: if [route] equals the target already pending
   * or currently being processed by a handler, the call is ignored. Lets users
   * double/triple-click without queueing duplicate navigations.
   */
  public fun handleRoute(route: DeepLinkTarget) {
    if (_pendingRoute.value?.route == route) return
    if (processingRoute == route) return
    _pendingRoute.value = PendingRoute(route, mode = null)
  }

  /**
   * Signal from the host that the root navigation stack has been mounted
   * and is safe to receive deep-link pushes. Must be called from the main
   * thread once after the host installs its root.
   */
  public fun setNavigationReady() {
    _isNavigationReady.value = true
  }

  /**
   * Drops the queued deep link without navigating to it.
   *
   * [pendingRoute] becomes null; a later [handleRoute] call for the same target starts over.
   */
  public fun clearPendingRoute() {
    _pendingRoute.value = null
  }

  private fun canNavigate(state: DeepLinkGateState): Boolean {
    val pending = state.pending ?: return false
    if (!state.isNavigationReady) return false
    if (!pending.route.requiresAuth) return true
    return state.isAuthenticated && state.isUnlocked
  }

  private fun dispatch(pending: PendingRoute) {
    // A handler already resolved this entry and chose the mode. Skip the
    // handler lookup (no chaining) and just navigate.
    if (pending.mode != null) {
      performNavigation(pending.mode, pending.route)
      _bootstrapTakenOver.value = true
      return
    }

    val handler = routeHandlers[pending.route::class]
    if (handler == null) {
      pushOrReplaceTop(pending.route)
      return
    }

    processingJob?.cancel()
    processingRoute = pending.route
    _handlerRunning.value = true

    @Suppress("UNCHECKED_CAST")
    val typed = handler as DeepLinkRouteHandler<DeepLinkTarget>

    processingJob = scope.launch(Dispatchers.Main) {
      try {
        val resolution = runHandlerCatching(typed, pending.route)

        when (resolution) {
          is DeepLinkResolution.Navigable ->
            _pendingRoute.value = PendingRoute(resolution.route, resolution.mode)
          DeepLinkResolution.Cancel -> Unit
        }
      } finally {
        // Only clear ownership if this launch is still the active one. A newer
        // dispatch that cancelled us will have overwritten [processingRoute]; in
        // that case the new dispatch owns the state and we must not stomp it.
        if (processingRoute == pending.route) {
          processingRoute = null
          _handlerRunning.value = false
        }
      }
    }
  }

  /**
   * Runs [handler] on [handlerDispatcher], mapping any failure to
   * [DeepLinkResolution.Cancel]. Coroutine cancellation propagates normally.
   */
  private suspend fun runHandlerCatching(
    handler: DeepLinkRouteHandler<DeepLinkTarget>,
    route: DeepLinkTarget,
  ): DeepLinkResolution {
    coroutineContext.ensureActive()
    return try {
      withContext(handlerDispatcher) { handler.handle(route) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      logger.error("Deep link handler failed for ${route::class.simpleName}", e)
      DeepLinkResolution.Cancel
    }
  }

  private fun performNavigation(mode: NavigationMode, route: DeepLinkTarget) {
    when (mode) {
      NavigationMode.Push -> pushOrReplaceTop(route)
      NavigationMode.Replace -> navigator.replace(route)
      NavigationMode.SetRoot -> navigator.setRoot(route)
      NavigationMode.Present -> navigator.present(route)
    }
  }

  /**
   * If the user is already on a screen of the same target type — e.g. a callback
   * re-firing `acme://profile/42` with new params — replace it instead of stacking a
   * duplicate, so the back button doesn't walk through stale copies. A deep link to a different
   * screen pushes normally. A plain in-app push still stacks, so detail→detail flows keep
   * their history.
   */
  private fun pushOrReplaceTop(route: DeepLinkTarget) {
    if (navigator.currentTargetClass() == route::class) {
      navigator.replace(route)
    } else {
      navigator.push(route)
    }
  }
}

private data class PendingRoute(val route: DeepLinkTarget, val mode: NavigationMode? = null)

private data class DeepLinkGateState(
  val pending: PendingRoute?,
  val isAuthenticated: Boolean,
  val isUnlocked: Boolean,
  val isNavigationReady: Boolean,
)
