package com.example.sample.routes

import dev.carcara.perch.DeepLink
import dev.carcara.perch.DeepLinkTarget

// No `@Serializable` on either class: `@DeepLink` is `@MetaSerializable`, so the serialisation
// plugin generates the serialiser from it alone.

/** A route with no path parameters. */
@DeepLink("/home")
public class HomeLink : DeepLinkTarget

/** A route carrying a path parameter, to prove parameters survive the pipeline. */
@DeepLink("/payments/{id}")
public class PaymentLink(public val id: String) : DeepLinkTarget
