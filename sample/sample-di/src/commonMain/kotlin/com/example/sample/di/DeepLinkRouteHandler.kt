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
