package dev.carcara.perch

/**
 * Where Perch reports a failure it recovered from: a route with no `@Resource` pattern. Adapt your
 * own logger to this; the default discards.
 */
public fun interface DeepLinkLogger {
  public fun error(message: String, throwable: Throwable?)

  public companion object {
    /** Discards every message. The default, so core depends on no logging framework. */
    public val None: DeepLinkLogger = DeepLinkLogger { _, _ -> }
  }
}
