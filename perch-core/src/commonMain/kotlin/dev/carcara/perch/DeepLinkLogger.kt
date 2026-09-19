/*
 * Copyright 2026 Carcara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.perch

/**
 * Where Perch reports what it recovered from rather than threw on: a route with no `@DeepLink`
 * pattern, or a parse against a parser with no routes registered. Both leave the app running with
 * a deep link that silently does not resolve. Adapt your own logger to it; the default discards.
 */
public fun interface DeepLinkLogger {
  public fun error(message: String, throwable: Throwable?)

  public companion object {
    /** Discards every message. The default, so core depends on no logging framework. */
    public val None: DeepLinkLogger = DeepLinkLogger { _, _ -> }
  }
}
