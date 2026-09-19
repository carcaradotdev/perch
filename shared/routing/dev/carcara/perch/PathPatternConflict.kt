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
 * The path-pattern rules, in the one place all three of Perch's Gradle builds can reach.
 *
 * `perch-core` enforces them at runtime, `perch-ksp` enforces them over one module at compile time,
 * and `perch-gradle-plugin` enforces them across every module an app aggregates. Those three live
 * in separate Gradle builds - the plugins are pulled in through `pluginManagement.includeBuild`, so
 * no project dependency can span them - and a rule written out three times is a rule that drifts.
 *
 * So this directory is added to each build's source set instead, and each compiles its own copy of
 * the same file. Nothing here may import anything: what makes the arrangement work is that the
 * file needs no dependency any one of the three lacks.
 *
 * Everything is `internal` deliberately. `perch-core` is published under binary-compatibility
 * validation, so a public declaration here would be a promise the 0.0.1 artifact could not take
 * back, for a rule no consumer is meant to call.
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
  // Dropping the empty pieces is what makes a trailing slash - and a doubled one - invisible here,
  // so no caller has to normalise the pattern before asking.
  val segments1 = pattern1.split("/").filter { it.isNotEmpty() }
  val segments2 = pattern2.split("/").filter { it.isNotEmpty() }

  if (segments1.size != segments2.size) {
    return couldMatchWithOptionals(segments1, segments2)
  }

  return segments1.indices.all { segmentsCouldMatch(segments1[it], segments2[it]) }
}

/**
 * Joins a route's own `@DeepLink` path to its parents', innermost first, the way a nested route's
 * pattern reads at runtime: `@DeepLink("/orders")` around `@DeepLink("/{id}")` is `orders/{id}`.
 *
 * Shared because both sides have to agree on it exactly. `perch-core` walks a serial descriptor to
 * collect the segments and `perch-ksp` walks a class declaration, so only the collecting differs -
 * and if the joining differed too, the pattern the processor checks for collisions would not be the
 * pattern the parser registers.
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
 *
 * Only [patternsConflict] calls this, and only once the counts differ, so `shorter` below really
 * is the shorter of the two.
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

/** Reports whether two single segments could match the same URL segment. */
private fun segmentsCouldMatch(seg1: String, seg2: String): Boolean {
  // Two constants conflict only when they are the same word. Any pairing that involves a parameter
  // conflicts, because the parameter matches whatever sits opposite it.
  val seg1IsParam = seg1.startsWith("{")
  val seg2IsParam = seg2.startsWith("{")

  return seg1IsParam || seg2IsParam || seg1 == seg2
}
