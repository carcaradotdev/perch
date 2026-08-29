package com.example.sample.routes

import dev.carcara.perch.DeepLink

/**
 * The app's own route type. Perch has no supertype of its own: this is what `targetBaseClass`
 * names in both build scripts, and what the generated `DeepLinkParser<SampleRoute>` extensions are
 * declared on.
 */
public sealed interface SampleRoute

// No `@Serializable` on either route: `@DeepLink` is `@MetaSerializable`, so the serialisation
// plugin generates the serialiser from it alone.

/** A route with no path parameters. */
@DeepLink("/home")
public class HomeLink : SampleRoute

/** A route carrying a path parameter, to prove parameters survive the pipeline. */
@DeepLink("/payments/{id}")
public class PaymentLink(public val id: String) : SampleRoute
