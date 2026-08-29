package com.example.sample.routes

import dev.carcara.perch.DeepLink

// The annotation is the whole contract: neither route implements anything, and no `@Serializable`
// is needed because `@DeepLink` is `@MetaSerializable`.

/** A route with no path parameters. */
@DeepLink("/home")
public class HomeLink

/** A route carrying a path parameter, to prove parameters survive the pipeline. */
@DeepLink("/payments/{id}")
public class PaymentLink(public val id: String)
