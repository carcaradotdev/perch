package com.example.sample.routes

import androidx.navigation3.runtime.NavKey
import dev.carcara.perch.DeepLink

// The annotation is the whole contract: `@DeepLink` is all Perch asks of a route, and no
// `@Serializable` is needed because `@DeepLink` is `@MetaSerializable`.
//
// Because Perch asks for nothing else, a route is free to satisfy whatever the app's navigation
// library asks for instead. This sample's app uses Navigation 3, whose back stack holds `NavKey`,
// so the routes implement `NavKey` and a parsed route goes onto that stack with no adapter in
// between. `NavKey` requires the key to be serializable for `rememberNavBackStack` to restore it,
// which `@DeepLink` has already arranged.
//
// The same trick does not work for every navigator, and that is the point of the sample app's
// other two screens: Voyager's `Screen` and Compose Destinations' `Direction` are UI types, so a
// route module implementing them would have to depend on Compose. Those two map instead, in the
// app module where the UI already lives.

/** A route with no path parameters. */
@DeepLink("/home")
public class HomeLink : NavKey

/** A route carrying a path parameter, to prove parameters survive the pipeline. */
@DeepLink("/payments/{id}")
public class PaymentLink(public val id: String) : NavKey
