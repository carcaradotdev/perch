package dev.carcara.perch.metro

import dev.carcara.perch.DeepLinkAuthGate
import dev.carcara.perch.DeepLinkBootstrapState
import dev.carcara.perch.DeepLinkLockGate
import dev.carcara.perch.DeepLinkLogger
import dev.carcara.perch.DeepLinkManager
import dev.carcara.perch.DeepLinkNavigator
import dev.carcara.perch.DeepLinkParser
import dev.carcara.perch.DeepLinkRouteHandler
import dev.carcara.perch.DeepLinkTarget
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.MapKey
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.reflect.KClass

/**
 * Wires [DeepLinkManager] into a Metro [AppScope] graph.
 *
 * `perch-core`'s [DeepLinkManager] carries no DI annotations of its own — it takes every
 * collaborator through a plain constructor with defaults, so `perch-core` depends on no DI
 * framework. This container is what gives a Metro consumer back the single-instance wiring the
 * plain constructor cannot express by itself: [provideDeepLinkManager] calls the constructor
 * explicitly and carries the `@SingleIn(AppScope::class)` scoping that used to live on the class.
 *
 * [DeepLinkNavigator], [DeepLinkParser], [CoroutineScope], [DeepLinkAuthGate] and
 * [DeepLinkLockGate] are app-specific — Perch cannot bind them for anyone — so a consumer must
 * provide all five elsewhere in the same graph, or Metro fails the graph at compile time rather
 * than at runtime. [DeepLinkRouteHandler] entries are optional: [DeepLinkRouteHandlerMapAccessor]
 * declares the multibinding as empty-allowed, so a consumer with no custom handlers needs to
 * provide nothing extra, and one with handlers registers them with `@IntoMap` and
 * [DeepLinkRouteKey] as usual. [DeepLinkLogger] is defaulted to [DeepLinkLogger.None] inside
 * [provideDeepLinkManager] rather than requested from the graph, so adopting this container never
 * forces a consumer to add a logging binding it does not otherwise need.
 */
@BindingContainer
@ContributesTo(AppScope::class)
public object DeepLinkBindings {

  /**
   * Dispatcher [DeepLinkManager] uses to invoke [DeepLinkRouteHandler.handle], so the
   * suspend work doesn't block the Main-thread scope. Injectable so tests can swap in
   * a deterministic test dispatcher.
   */
  @Provides
  @Named("deepLinkHandlerDispatcher")
  public fun provideDeepLinkHandlerDispatcher(): CoroutineDispatcher = Dispatchers.IO

  /**
   * Builds the single [DeepLinkManager] for the graph. [navigator], [parser], [scope],
   * [authGate] and [lockGate] all come from the consumer's own bindings; [routeHandlers]
   * resolves from the (possibly empty) multibinding declared by
   * [DeepLinkRouteHandlerMapAccessor]; [handlerDispatcher] is
   * [provideDeepLinkHandlerDispatcher] above. [DeepLinkLogger] keeps its constructor default
   * rather than being requested here, so this container does not oblige a consumer to bind one.
   */
  @Provides
  @SingleIn(AppScope::class)
  public fun provideDeepLinkManager(
    navigator: DeepLinkNavigator,
    parser: DeepLinkParser,
    scope: CoroutineScope,
    authGate: DeepLinkAuthGate,
    lockGate: DeepLinkLockGate,
    routeHandlers: Map<KClass<out DeepLinkTarget>, DeepLinkRouteHandler<*>>,
    @Named("deepLinkHandlerDispatcher") handlerDispatcher: CoroutineDispatcher,
  ): DeepLinkManager = DeepLinkManager(
    navigator = navigator,
    parser = parser,
    scope = scope,
    authGate = authGate,
    lockGate = lockGate,
    routeHandlers = routeHandlers,
    handlerDispatcher = handlerDispatcher,
  )

  /** Exposes [DeepLinkManager] as the read-only [DeepLinkBootstrapState] view. */
  @Provides
  public fun provideDeepLinkBootstrapState(manager: DeepLinkManager): DeepLinkBootstrapState = manager
}

/** Empty-allowed multibind for [DeepLinkRouteHandler] so [DeepLinkManager] can inject the map even when no handlers are registered. */
@ContributesTo(AppScope::class)
public interface DeepLinkRouteHandlerMapAccessor {
  @Multibinds(allowEmpty = true)
  public val deepLinkRouteHandlers: Map<KClass<out DeepLinkTarget>, DeepLinkRouteHandler<*>>
}

/**
 * Map key for [DeepLinkRouteHandler] multibindings. The value is the concrete [DeepLinkTarget]
 * subclass the handler resolves.
 */
@MapKey
public annotation class DeepLinkRouteKey(val value: KClass<out DeepLinkTarget>)
