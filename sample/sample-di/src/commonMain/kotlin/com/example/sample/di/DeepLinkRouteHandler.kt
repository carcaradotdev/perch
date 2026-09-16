package com.example.sample.di

import com.example.sample.navigation.SampleRoute
import dev.zacsweers.metro.MapKey
import kotlin.reflect.KClass

/**
 * A feature's say in what happens when one of its own links is opened.
 *
 * Perch resolves a URL to a route and stops there, so this is the app's layer, not the library's.
 * A feature registers one by annotating it:
 *
 * ```kotlin
 * @ContributesIntoMap(AppScope::class, binding = binding<DeepLinkRouteHandler<*>>())
 * @DeepLinkRouteKey(PaymentRoutes.Approvals::class)
 * @Inject
 * internal class ApprovalsHandler(private val session: SessionState) :
 *   DeepLinkRouteHandler<PaymentRoutes.Approvals> { ... }
 * ```
 *
 * The explicit `binding` is load-bearing: without it Metro binds the supertype as written, and a
 * map keyed on `DeepLinkRouteHandler<PaymentRoutes.Approvals>` is not the star-projected map the
 * router injects.
 *
 * Which pairs with how the links themselves arrive: a feature declares `@DeepLink` on a route and
 * Perch's aggregator finds it, a feature declares a handler and Metro's graph finds it. Neither
 * needs a line in a central list, so adding a feature is adding a module.
 */
public fun interface DeepLinkRouteHandler<R : SampleRoute> {

  /** Where the user actually lands. Returning [route] unchanged is the common answer. */
  public fun resolve(route: R): SampleRoute
}

/** Keys a [DeepLinkRouteHandler] by the route class it answers for. */
@MapKey
public annotation class DeepLinkRouteKey(val value: KClass<out SampleRoute>)
