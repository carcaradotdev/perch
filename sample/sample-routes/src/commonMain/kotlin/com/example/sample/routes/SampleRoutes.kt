package com.example.sample.routes

import dev.carcara.perch.DeepLinkTarget
import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/** A route with no path parameters. */
@Serializable
@Resource("/home")
public class HomeLink : DeepLinkTarget

/** A route carrying a path parameter, to prove parameters survive the pipeline. */
@Serializable
@Resource("/payments/{id}")
public class PaymentLink(public val id: String) : DeepLinkTarget
