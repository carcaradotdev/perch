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

/*
 * The path-pattern rules, enforced by `perch-core` at runtime, by `perch-ksp` over one module, and
 * by `perch-gradle-plugin` across every module an app aggregates. Those are separate Gradle builds
 * - the plugins arrive through `pluginManagement.includeBuild` - so each adds this directory to its
 * own source set and compiles its own copy. Nothing here may import anything.
 *
 * Everything is `internal`: `perch-core` is published under binary-compatibility validation, and a
 * public declaration here would be a promise the artifact could not take back.
 */

/**
 * Reports whether two path patterns could match the same URL, which is what [DeepLinkParser]
 * treats as a registration collision.
 *
 * Conflicts:
 * - `/feature/list` against `/feature/list/`, which differ only by a trailing slash
 * - `/feature/list` against `/feature/{id}`, where the parameter also matches the constant
 * - `/feature/{id}` against `/feature/{name}`, which are structurally identical
 *
 * Non-conflicts:
 * - `/feature/list` against `/feature/details`, two different constants
 * - `/feature/list` against `/feature/list/details`, a different segment count
 */
internal fun patternsConflict(pattern1: String, pattern2: String): Boolean {
  // Dropping the empty pieces makes a trailing - or doubled - slash invisible, so no caller has to
  // normalise the pattern first.
  val segments1 = pattern1.split("/").filter { it.isNotEmpty() }
  val segments2 = pattern2.split("/").filter { it.isNotEmpty() }

  if (segments1.size != segments2.size) {
    return couldMatchWithOptionals(segments1, segments2)
  }

  return segments1.indices.all { segmentsCouldMatch(segments1[it], segments2[it]) }
}

/**
 * Joins a route's own `@DeepLink` path to its parents', innermost first: `@DeepLink("/orders")`
 * around `@DeepLink("/{id}")` is `orders/{id}`.
 *
 * `perch-core` collects the segments off a serial descriptor and `perch-ksp` off a class
 * declaration. Only the collecting may differ: if the joining did too, the pattern the processor
 * checks for collisions would not be the one the parser registers.
 */
internal fun joinDeepLinkPattern(segmentsInnermostFirst: List<String>): String {
  val path = StringBuilder()
  for (segment in segmentsInnermostFirst) {
    if (path.isNotEmpty() && !path.startsWith('/') && !segment.endsWith('/')) {
      path.insert(0, '/')
    }
    path.insert(0, segment)
  }
  if (path.startsWith('/')) {
    path.deleteAt(0)
  }
  return path.toString()
}

/**
 * Reports whether patterns of different segment counts could still conflict, which happens when
 * the shorter one ends in a tailcard or the longer one's extra segments are all optional.
 */
private fun couldMatchWithOptionals(segments1: List<String>, segments2: List<String>): Boolean {
  val (shorter, longer) =
    if (segments1.size < segments2.size) segments1 to segments2 else segments2 to segments1

  val last = shorter.lastOrNull()
  if (last != null && last.startsWith("{") && last.endsWith("...}")) {
    // A tailcard matches any number of remaining segments, so only what precedes it has to line up.
    return (0 until shorter.size - 1).all { segmentsCouldMatch(shorter[it], longer[it]) }
  }

  if (shorter.indices.any { !segmentsCouldMatch(shorter[it], longer[it]) }) return false

  // The longer pattern only reaches the shorter one's length if everything past it can be omitted.
  return longer.subList(shorter.size, longer.size).all { it.startsWith("{") && it.endsWith("?}") }
}

/**
 * Reports whether two single segments could match the same URL segment. Two constants conflict
 * only when they are the same word; any pairing involving a parameter conflicts, because the
 * parameter matches whatever sits opposite it.
 */
private fun segmentsCouldMatch(seg1: String, seg2: String): Boolean {
  val seg1IsParam = seg1.startsWith("{")
  val seg2IsParam = seg2.startsWith("{")

  return seg1IsParam || seg2IsParam || seg1 == seg2
}
