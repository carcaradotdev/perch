package dev.carcara.perch

import io.ktor.resources.href
import io.ktor.resources.serialization.ResourcesFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Parses deep-link URLs into type-safe [DeepLinkTarget] objects.
 *
 * Pattern matching follows Ktor's routing conventions:
 * - `{param}` required path parameter
 * - `{param?}` optional path parameter
 * - `{param...}` tailcard, matching the remaining segments
 *
 * A URL resolves only when its scheme is in [schemes], compared case-insensitively.
 *
 * Whether a URL has a host is decided by its scheme, not by what the URL looks like. `http` and
 * `https` are hierarchical: the authority — everything between `://` and the next `/`, `?`, or
 * `#` — is the host, with any `userinfo@` prefix and `:port` suffix dropped and an empty
 * authority rejected as malformed. For those two schemes, and only those two, a non-empty [hosts]
 * must contain the URL's host, which is what keeps a look-alike site from resolving a route the
 * app owns. Every other scheme, and a URL with no scheme at all, has no host: `acme://payments/abc`
 * merely puts its first path element where a host would sit, so [hosts] is not consulted and the
 * whole URL is matched as a path.
 *
 * ```kotlin
 * val parser = DeepLinkParser(schemes = setOf("acme", "https"), hosts = setOf("acme.com"))
 * parser.register<PaymentDeepLink>()
 *
 * when (val link = parser.parse("acme://payments/abc123")) {
 *   is PaymentDeepLink -> navigator.push(link)
 *   else -> Unit
 * }
 *
 * parser.toUrl(PaymentDeepLink("abc123")) // "acme://payments/abc123"
 * ```
 *
 * Registration is per instance. Two parsers never share routes.
 */
public class DeepLinkParser(
  schemes: Set<String>,
  hosts: Set<String> = emptySet(),
  private val logger: DeepLinkLogger = DeepLinkLogger.None,
) {

  init {
    require(schemes.isNotEmpty()) { "DeepLinkParser needs at least one scheme" }
  }

  private val schemes: Set<String> = schemes.map { it.lowercase() }.toSet()
  private val hosts: Set<String> = hosts.map { it.lowercase() }.toSet()

  /**
   * Scheme [toUrl] emits, which is the first entry of [schemes]. `@PublishedApi internal`
   * rather than private: [toUrl] is inline with a reified type, so it cannot read a private member.
   */
  @PublishedApi
  internal val canonicalScheme: String = schemes.first()

  @PublishedApi
  internal val format: ResourcesFormat = ResourcesFormat()

  private val registeredRoutes = mutableListOf<RegisteredRoute<*>>()
  private val registeredPatterns = mutableMapOf<String, String>()

  public inline fun <reified T : DeepLinkTarget> register() {
    register(serializer<T>())
  }

  public fun <T : DeepLinkTarget> register(serializer: KSerializer<T>) {
    val routeName = serializer.descriptor.serialName
    // A route with no @Resource path pattern cannot be a deep link. This happens when a stale
    // generated registration still names a route whose @Resource was removed. Skip it rather
    // than let one bad entry crash app startup; the deep link just will not resolve.
    val pathPattern = try {
      format.encodeToPathPattern(serializer)
    } catch (error: Exception) {
      logger.error("Skipping deep link route '$routeName' with no @Resource path pattern", error)
      return
    }
    val normalizedPattern = pathPattern.trimEnd('/')

    for ((existingPattern, existingRoute) in registeredPatterns) {
      if (existingRoute == routeName) return
      if (patternsConflict(normalizedPattern, existingPattern.trimEnd('/'))) {
        throw DeepLinkCollisionException(
          pattern = pathPattern,
          existingRoute = existingRoute,
          newRoute = routeName,
        )
      }
    }

    registeredPatterns[pathPattern] = routeName
    registeredRoutes.add(RegisteredRoute(serializer, pathPattern, format))
  }

  public fun parse(url: String): DeepLinkTarget? {
    val location = UrlLocation.of(url) ?: return null
    if (location.scheme != null && location.scheme !in schemes) return null
    if (location.host != null && hosts.isNotEmpty() && location.host !in hosts) return null

    for (route in registeredRoutes) {
      val result = route.tryParse(location.pathSegments, location.queryParameters)
      if (result != null) return result
    }
    return null
  }

  public inline fun <reified T : DeepLinkTarget> toUrl(deepLink: T): String {
    val path = href(format, deepLink)
    return "$canonicalScheme:/$path"
  }
}

/** Thrown when two routes register path patterns that could match the same URL. */
public class DeepLinkCollisionException(
  public val pattern: String,
  public val existingRoute: String,
  public val newRoute: String,
) : IllegalStateException(
  "Deep link collision detected. Pattern '$pattern' is already registered by '$existingRoute', " +
    "so '$newRoute' cannot register the same pattern.",
)
