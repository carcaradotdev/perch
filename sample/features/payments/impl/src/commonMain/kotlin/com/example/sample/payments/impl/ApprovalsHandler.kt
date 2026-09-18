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

package com.example.sample.payments.impl

import com.example.sample.di.AppScope
import com.example.sample.di.DeepLinkRouteHandler
import com.example.sample.di.DeepLinkRouteKey
import com.example.sample.di.SessionState
import com.example.sample.home.api.HomeDeepLink
import com.example.sample.navigation.SampleRoute
import com.example.sample.payments.api.PaymentRoutes
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/**
 * Sends a signed-out visitor to home instead of the approvals list.
 *
 * The gate is here, in the feature that owns the link, and the router never learns it exists. What
 * reaches the router is a map it was handed; what reaches this class is the [SessionState] it
 * asked for. The two modules are connected by the graph and by nothing else - no registration
 * call, and no argument threaded down from the shell.
 *
 * `internal` on a contributed binding is deliberate: the graph module needs the binding, not the
 * type.
 *
 * The explicit `binding` is what puts it in the map the router injects. Left off, Metro binds the
 * sole supertype as written - `DeepLinkRouteHandler<PaymentRoutes.Approvals>` - and a map keyed on
 * the star projection is a different map, which stays empty while this handler sits in one nobody
 * reads.
 */
@ContributesIntoMap(AppScope::class, binding = binding<DeepLinkRouteHandler<*>>())
@DeepLinkRouteKey(PaymentRoutes.Approvals::class)
@Inject
internal class ApprovalsHandler(
  private val session: SessionState,
) : DeepLinkRouteHandler<PaymentRoutes.Approvals> {

  override fun resolve(route: PaymentRoutes.Approvals): SampleRoute =
    if (session.signedIn) route else HomeDeepLink
}
