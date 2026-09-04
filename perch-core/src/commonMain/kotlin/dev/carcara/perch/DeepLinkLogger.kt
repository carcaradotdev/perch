package dev.carcara.perch

/**
 * Where Perch reports something it recovered from rather than threw on: a route with no `@DeepLink`
 * pattern, or a parse against a parser nobody registered a route on. Both leave the app running
 * with a deep link that silently does not resolve, which is the failure this channel exists to make
 * visible. Adapt your own logger to it; the default discards.
 */
public fun interface DeepLinkLogger {
  public fun error(message: String, throwable: Throwable?)

  public companion object {
    /** Discards every message. The default, so core depends on no logging framework. */
    public val None: DeepLinkLogger = DeepLinkLogger { _, _ -> }
  }
}
