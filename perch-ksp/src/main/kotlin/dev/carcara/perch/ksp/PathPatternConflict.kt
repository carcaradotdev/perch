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

package dev.carcara.perch.ksp

/**
 * Reports whether two path patterns could match the same URL.
 *
 * This is a second implementation of the rule `perch-core`'s `patternsConflict` enforces at
 * runtime. The two cannot share code: this module is JVM-only and depends on the KSP API, while
 * core is Kotlin Multiplatform and would drag Ktor onto the KSP classpath. `DeepLinkProcessorTest`
 * asserts the two agree, which is what keeps the duplication honest.
 *
 * Examples of conflicts:
 * - `/feature/list` vs `/feature/list/` (trailing slash)
 * - `/feature/list` vs `/feature/{id}` (parameter matches constant)
 * - `/feature/{id}` vs `/feature/{name}` (structurally identical)
 */
internal fun patternsConflict(pattern1: String, pattern2: String): Boolean {
  val normalized1 = pattern1.trimEnd('/')
  val normalized2 = pattern2.trimEnd('/')

  val segments1 = normalized1.split("/").filter { it.isNotEmpty() }
  val segments2 = normalized2.split("/").filter { it.isNotEmpty() }

  if (segments1.size != segments2.size) {
    return couldMatchWithOptionals(segments1, segments2)
  }

  for (i in segments1.indices) {
    val seg1 = segments1[i]
    val seg2 = segments2[i]

    val seg1IsParam = seg1.startsWith("{") && seg1.endsWith("}")
    val seg2IsParam = seg2.startsWith("{") && seg2.endsWith("}")

    if (!seg1IsParam && !seg2IsParam && seg1 != seg2) {
      return false
    }
  }

  return true
}

/**
 * Reports whether patterns of different segment counts could still conflict, which happens when
 * the shorter one ends in a tailcard or the longer one's extra segments are all optional.
 */
internal fun couldMatchWithOptionals(segments1: List<String>, segments2: List<String>): Boolean {
  val shorter = if (segments1.size < segments2.size) segments1 else segments2
  val longer = if (segments1.size < segments2.size) segments2 else segments1

  if (shorter.isNotEmpty()) {
    val lastSeg = shorter.last()
    if (lastSeg.startsWith("{") && lastSeg.endsWith("...}")) {
      for (i in 0 until shorter.size - 1) {
        val seg2 = longer.getOrNull(i) ?: return false
        if (!segmentsCouldMatch(shorter[i], seg2)) return false
      }
      return true
    }
  }

  for (i in shorter.indices) {
    if (!segmentsCouldMatch(shorter[i], longer[i])) return false
  }
  for (i in shorter.size until longer.size) {
    val seg = longer[i]
    val isOptional = seg.startsWith("{") && seg.endsWith("?}")
    if (!isOptional) return false
  }
  return true
}

/** Reports whether two single segments could match the same URL segment. */
internal fun segmentsCouldMatch(seg1: String, seg2: String): Boolean {
  val seg1IsParam = seg1.startsWith("{")
  val seg2IsParam = seg2.startsWith("{")
  return if (!seg1IsParam && !seg2IsParam) seg1 == seg2 else true
}
