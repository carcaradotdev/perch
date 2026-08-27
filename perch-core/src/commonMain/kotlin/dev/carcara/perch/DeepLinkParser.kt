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
 * A URL that carries a scheme resolves only when that scheme is in [schemes], compared
 * case-insensitively. **A URL with no scheme is matched as a path and faces neither the scheme
 * check nor the host check** — `payments/abc123` and `/payments/abc123` both resolve. The marginal
 * risk is nil, since anyone able to choose the string could pass a bare path anyway, but do not
 * read [schemes] as a guarantee about every input. A string carrying a scheme in some form other
 * than `scheme://`, such as `https:/evil.example/x`, is rejected rather than treated as a path.
 *
 * Whether a URL has a host is decided by its scheme, not by what the URL looks like. `http` and
 * `https` are hierarchical: the authority — everything between `://` and the next `/`, `?`, `#`, or
 * `\` — is the host, with any `userinfo@` prefix and `:port` suffix dropped and an empty authority
 * rejected as malformed. An IPv6 host keeps its brackets, so configure it as `setOf("[::1]")`. For
 * those two schemes, and only those two, [hosts] must contain the URL's host, which is what keeps a
 * look-alike site from resolving a route the app owns. Every other scheme has no host:
 * `acme://payments/abc` merely puts its first path element where a host would sit, so [hosts] is
 * not consulted.
 *
 * Because an empty [hosts] would mean "any website may deep-link into these routes", the
 * constructor **rejects `http` or `https` in [schemes] unless [hosts] is non-empty**. Custom
 * schemes are unaffected: for them [hosts] is meaningless and may stay empty.
 *
 * A fragment is discarded, in every scheme, before the path and query are read. Surrounding
 * whitespace is trimmed. Nothing is percent-decoded.
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

  private val schemes: Set<String> = schemes.map { it.lowercase() }.toSet()
  private val hosts: Set<String> = hosts.map { it.lowercase() }.toSet()

  init {
    // `this.` throughout: a constructor parameter shadows the property of the same name inside an
    // initialiser, and the parameters here are the un-lowercased originals.
    require(this.schemes.isNotEmpty()) { "DeepLinkParser needs at least one scheme" }
    val hierarchical = this.schemes.filter { it in HIERARCHICAL_SCHEMES }
    require(hierarchical.isEmpty() || this.hosts.isNotEmpty()) {
      "DeepLinkParser was given $hierarchical with no hosts, which lets any website on the " +
        "internet deep-link into these routes. Pass the domains you own, for example " +
        "hosts = setOf(\"example.com\"), or drop http and https from schemes."
    }
  }

  /**
   * Scheme [toUrl] emits, which is the first entry of [schemes]. `@PublishedApi internal`
   * rather than private: [toUrl] is inline with a reified type, so it cannot read a private member.
   */
  @PublishedApi
  internal val canonicalScheme: String = this.schemes.first()

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
