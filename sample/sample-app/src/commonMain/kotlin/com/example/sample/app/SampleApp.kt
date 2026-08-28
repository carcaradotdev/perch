package com.example.sample.app

import dev.carcara.perch.DeepLinkParser

/**
 * Parser wired the way a consuming app wires one: construct, then register the generated set.
 *
 * `registerAllDeepLinks()` comes from `PerchDeepLinkRegistration.kt`, which the
 * `generateDeepLinkRegistration` task writes into `commonMain`. This file does not compile until
 * the whole pipeline - KSP scan, manifest publication, aggregation - has run, which is the point.
 *
 * `hosts` is non-empty because `schemes` carries `https`: `DeepLinkParser` rejects `http` or
 * `https` without hosts, since an empty host set would let any website deep-link into these routes.
 */
public fun sampleParser(): DeepLinkParser =
  DeepLinkParser(schemes = setOf("sample", "https"), hosts = setOf("sample.example"))
    .apply { registerAllDeepLinks() }
