package dev.carcara.perch

import dev.carcara.perch.serialization.DeepLinkFormat
import dev.carcara.perch.serialization.encodeToPath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Parses deep-link URLs into type-safe [DeepLinkTarget] objects.
 *
 * A route is a class annotated [DeepLink], whose path pattern decides which of its properties come
 * out of the path:
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
 * whitespace is trimmed. Path segments and query components are percent-decoded one at a time,
 * after the URL has been split, so `%2F` lands inside a parameter's value instead of opening a new
 * segment; this is the inverse of what [toUrl] encodes. The path is not normalised: `.` and `..`
 * are ordinary segments here, so a `{param...}` tailcard route receives them verbatim rather than
 * having them resolved away.
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
   * What [toUrl] puts in front of the encoded path: `"<scheme>:/"` for a custom scheme, or
   * `"https://<host>"` when every configured scheme is hierarchical.
   */
  private val urlPrefix: String = buildUrlPrefix(this.schemes, this.hosts)

  private val format = DeepLinkFormat()

  private val registeredRoutes = mutableListOf<RegisteredRoute<*>>()
  private val registeredPatterns = mutableMapOf<String, String>()

  public inline fun <reified T : DeepLinkTarget> register() {
    register(serializer<T>())
  }

  public fun <T : DeepLinkTarget> register(serializer: KSerializer<T>) {
    val routeName = serializer.descriptor.serialName
    // A route with no @DeepLink path pattern cannot be a deep link. This happens when a stale
    // generated registration still names a route whose @DeepLink was removed. Skip it rather
    // than let one bad entry crash app startup; the deep link just will not resolve.
    val pathPattern = try {
      format.encodeToPathPattern(serializer)
    } catch (error: Exception) {
      logger.error("Skipping deep link route '$routeName' with no @DeepLink path pattern", error)
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

  /**
   * Builds the URL for [deepLink].
   *
   * The scheme is the first *custom* (non-`http`, non-`https`) entry of `schemes`, whatever order
   * `schemes` was given in. Only a custom scheme puts a route's first path element straight after
   * `://`; a hierarchical one puts a host there. When every configured scheme is hierarchical the
   * URL is emitted as `https://<first host>/...` instead, against the first entry of `hosts`.
   * Either way the result parses back through [parse].
   *
   * Path segments and query components are percent-encoded, so a value carrying a slash, a space
   * or a non-ASCII character survives the round trip.
   *
   * @throws DeepLinkSerializationException when [deepLink] is not a `@DeepLink` route, or a
   * required placeholder in its path has no value to fill it.
   */
  public inline fun <reified T : DeepLinkTarget> toUrl(deepLink: T): String =
    toUrl(serializer<T>(), deepLink)

  /** [toUrl] for a serialiser resolved by the caller, rather than reified at the call site. */
  public fun <T : DeepLinkTarget> toUrl(serializer: KSerializer<T>, deepLink: T): String =
    "$urlPrefix${format.encodeToPath(serializer, deepLink)}"
}

/**
 * Prefix `toUrl` emits ahead of the encoded path, which always starts with `/`.
 *
 * A custom scheme wins over `http`/`https` regardless of iteration order, and iteration order is
 * the point: `schemes` is a `Set` the consumer supplies, whose order is not part of `Set`'s
 * contract. Emitting the first entry blindly turns `setOf("https", "myapp")` into
 * `https://payments/abc`, which names `payments` as the host and does not parse back. Only when
 * every configured scheme is hierarchical is one emitted, and then with a real host in the host
 * position — `hosts` is non-empty in that case, because the constructor requires it.
 */
private fun buildUrlPrefix(schemes: Set<String>, hosts: Set<String>): String {
  val customScheme = schemes.firstOrNull { it !in HIERARCHICAL_SCHEMES }
  if (customScheme != null) return "$customScheme:/"
  return "${schemes.first()}://${hosts.first()}"
}

/** Thrown when two routes register path patterns that could match the same URL. */
public class DeepLinkCollisionException(
  public val pattern: String,
  public val existingRoute: String,
  public val newRoute: String,
) : IllegalStateException(
  "Deep link collision detected. Pattern '$pattern' conflicts with a pattern already registered " +
    "by '$existingRoute', so '$newRoute' cannot be registered.",
)
