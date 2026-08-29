/*
 * Derived from io/ktor/resources/Resource.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MetaSerializable
import kotlinx.serialization.SerialInfo

/**
 * Marks a class as a deep-link route and gives it a [path] pattern.
 *
 * The annotation is `@MetaSerializable`, so an annotated class is serialisable without also being
 * annotated `@Serializable`. Every property whose name appears as a `{placeholder}` in [path] fills
 * that placeholder; the rest become query parameters.
 *
 * ```kotlin
 * @DeepLink("/payments/{id}")
 * class PaymentLink(val id: String, val tab: String? = null) : DeepLinkTarget
 * // acme://payments/abc123?tab=receipt
 * ```
 *
 * Placeholder forms, matching Ktor's routing conventions:
 * - `{name}` a required path parameter
 * - `{name?}` an optional path parameter, which must be the last segment
 * - `{name...}` a tailcard capturing every remaining segment
 *
 * Routes may nest for grouping, in which case the child's path is appended to the parent's. A
 * nested route declares a property of the parent's type, and that property is what links the two:
 *
 * ```kotlin
 * @DeepLink("/orders")
 * class Orders : DeepLinkTarget {
 *   @DeepLink("/{id}")
 *   class ById(val parent: Orders = Orders(), val id: String) : DeepLinkTarget
 * }
 * ```
 *
 * A class may have at most one such parent property, and a class annotated `@DeepLink` that is not
 * registered with a [DeepLinkParser] does nothing.
 *
 * @property path the route path, with property names wrapped in curly braces.
 */
@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@MetaSerializable
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class DeepLink(val path: String)
