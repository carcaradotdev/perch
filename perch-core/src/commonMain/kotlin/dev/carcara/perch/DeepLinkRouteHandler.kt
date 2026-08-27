package dev.carcara.perch

/**
 * Custom resolver invoked by [DeepLinkManager] before navigating to a deep-link [DeepLinkTarget].
 *
 * Use a handler when navigating to the original route requires a long-running setup step
 * (fetching state, resolving an id, deciding whether the user is allowed to land on the
 * screen, etc.). The handler runs as a `suspend` function on the IO dispatcher and the
 * cold-start splash stays visible while it executes; warm-start runs silently in the
 * background with no extra UI.
 *
 * Register by putting it in the handler map you pass to [DeepLinkManager]:
 * ```kotlin
 * DeepLinkManager(
 *   navigator = navigator,
 *   parser = parser,
 *   scope = scope,
 *   routeHandlers = mapOf(MyDeepLinkRoute::class to MyDeepLinkRouteHandler(service)),
 * )
 * ```
 *
 * Cold-start consideration: handlers reachable from a cold start should return
 * [NavigationMode.SetRoot] (or [NavigationMode.Replace]) so the user lands on a usable
 * stack. Returning [NavigationMode.Push] from cold start lands the user on the target
 * with no entry behind it.
 */
public fun interface DeepLinkRouteHandler<R : DeepLinkTarget> {
  public suspend fun handle(route: R): DeepLinkResolution
}

/**
 * Outcome of a [DeepLinkRouteHandler.handle] call.
 *
 * - [Resolved]: navigate to the original target route.
 * - [Fallback]: handler hit a recoverable error (e.g. user not eligible) and is
 *   redirecting elsewhere (Home, Login, an error screen, etc.).
 * - [Cancel]: drop the deep link entirely. [DeepLinkManager] performs no navigation;
 *   normal cold-start flow (the startup presenter) resolves the destination as if the
 *   deep link had never arrived.
 */
public sealed interface DeepLinkResolution {
  /** Resolutions that should produce navigation. */
  public sealed interface Navigable : DeepLinkResolution {
    public val route: DeepLinkTarget
    public val mode: NavigationMode
  }

  public data class Resolved(
    override val route: DeepLinkTarget,
    override val mode: NavigationMode = NavigationMode.Push,
  ) : Navigable

  public data class Fallback(
    override val route: DeepLinkTarget,
    override val mode: NavigationMode = NavigationMode.Push,
  ) : Navigable

  public data object Cancel : DeepLinkResolution
}

/** How [DeepLinkManager] should hand a resolved route to the [DeepLinkNavigator]. */
public sealed class NavigationMode {
  public data object Push : NavigationMode()
  public data object Replace : NavigationMode()
  public data object SetRoot : NavigationMode()
  public data object Present : NavigationMode()
}
