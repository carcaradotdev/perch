package com.example.sample.di

import com.example.sample.navigation.SampleRoute
import dev.carcara.perch.DeepLinkParser
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass

/**
 * What the router did with a URL, so a caller can say it out loud.
 *
 * [Navigated] carries both routes because they are not always the same one: a feature's handler
 * may send the user somewhere else, and "you asked for approvals and got home" is the sort of
 * thing worth being able to explain.
 */
public sealed interface Routing {

  public data class Navigated(val matched: SampleRoute, val destination: SampleRoute) : Routing

  /** No feature claims this URL. The app carries on with whatever it was showing. */
  public data object Unmatched : Routing
}

/**
 * The one object the shell talks to: URL in, navigation out.
 *
 * Every collaborator arrives through the constructor, and the interesting one is [parser]. It is
 * the aggregated parser, every feature's links already in it, and the graph module is the one place
 * that fact is stated. Nothing here calls `sampleParser()` or knows which features exist.
 */
@SingleIn(AppScope::class)
@Inject
public class DeepLinkRouter(
  private val parser: DeepLinkParser,
  private val navigator: DeepLinkNavigator,
  private val handlers: Map<KClass<out SampleRoute>, DeepLinkRouteHandler<*>>,
) {

  public fun open(url: String): Routing {
    // `parse` returns Any?, because Perch asks nothing of a route class. SampleRoute is this app's
    // own narrowing, and the cast failing is the same answer as no match at all.
    val matched = parser.parse(url) as? SampleRoute ?: return Routing.Unmatched

    val destination = handlers[matched::class]?.resolve(matched) ?: matched
    navigator.goTo(destination)
    return Routing.Navigated(matched = matched, destination = destination)
  }

  // The map key is the route class the handler was registered under, so a handler is only ever
  // reached with the type it declared. The generics cannot express that, hence the cast.
  @Suppress("UNCHECKED_CAST")
  private fun DeepLinkRouteHandler<*>.resolve(route: SampleRoute): SampleRoute =
    (this as DeepLinkRouteHandler<SampleRoute>).resolve(route)
}

/**
 * Declares the handler map so the graph compiles with no handlers in it.
 *
 * A feature that contributes none is the normal case - home has no handler - and without this the
 * map would have to be contributed to before [DeepLinkRouter] could ask for it.
 */
@ContributesTo(AppScope::class)
public interface DeepLinkRouteHandlerMap {

  @Multibinds(allowEmpty = true)
  public val deepLinkRouteHandlers: Map<KClass<out SampleRoute>, DeepLinkRouteHandler<*>>
}
