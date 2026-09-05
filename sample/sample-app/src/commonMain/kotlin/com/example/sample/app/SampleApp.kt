package com.example.sample.app

import dev.carcara.perch.DeepLinkParser

/**
 * Parser wired the way a consuming app wires one: one call, with every feature's links already in
 * it.
 *
 * `perchParser()` comes from `PerchDeepLinkRegistration.kt`, which the
 * `generateDeepLinkRegistration` task writes into `commonMain` after walking this module's own
 * dependencies for the manifests each feature published. This file does not compile until that
 * whole pipeline - KSP scan in every feature, manifest publication, aggregation here - has run,
 * which is the point.
 *
 * `hosts` is non-empty because `schemes` carries `https`: `DeepLinkParser` rejects `http` or
 * `https` without hosts, since an empty host set would let any website deep-link into these routes.
 */
public fun sampleParser(): DeepLinkParser =
  perchParser(schemes = setOf("sample", "https"), hosts = setOf("sample.example"))
