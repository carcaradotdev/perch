package com.example.sample.routes

import dev.carcara.perch.DeepLinkTarget
import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/** An open route: reachable without a signed-in user. */
@Serializable
@Resource("/home")
public class HomeLink : DeepLinkTarget {
  override val requiresAuth: Boolean get() = false
}

/** A gated route carrying a path parameter, to prove parameters survive the pipeline. */
@Serializable
@Resource("/payments/{id}")
public class PaymentLink(public val id: String) : DeepLinkTarget {
  override val requiresAuth: Boolean get() = true
}
