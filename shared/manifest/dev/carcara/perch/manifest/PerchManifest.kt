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

package dev.carcara.perch.manifest

/*
 * The route manifest: what `perch-ksp` writes for a module and what `perch-gradle-plugin` reads
 * back across every module an app depends on. Those are separate Gradle builds, so each compiles
 * its own copy of this file, the same arrangement as `shared/routing`. Nothing here may import
 * anything.
 *
 * Both sides share it because neither fails loudly when they disagree: an aggregator that matches
 * no manifest warns exactly as a build with no routes in it does.
 */

/** Filename `perch-ksp` writes and `perch-gradle-plugin` matches, minus the module's own part. */
internal const val MANIFEST_FILE_PREFIX: String = "perch-manifest-"

internal const val MANIFEST_FILE_EXTENSION: String = "txt"

/** Ant-style pattern that selects manifests out of a dependency's whole resources directory. */
internal const val MANIFEST_FILE_PATTERN: String = "$MANIFEST_FILE_PREFIX*.$MANIFEST_FILE_EXTENSION"

private const val FIELD_SEPARATOR = "|"

/** One route, as a manifest records it. */
internal data class ManifestRoute(
  /** The full path pattern, parents included, exactly as the parser will register it. */
  val pattern: String,
  /** Fully qualified, because two modules naming a route `Details` is ordinary rather than rare. */
  val routeClassName: String,
  /** The Gradle path of the module that declared it, which is what a collision message needs. */
  val moduleId: String,
)

/**
 * The manifest filename for [moduleId], named after the module rather than after the package it
 * generates into: two modules sharing an output package would otherwise write manifests of the
 * same name, which the aggregator's diagnostics print.
 */
internal fun manifestFileNameFor(moduleId: String): String {
  val slug = moduleId.map { if (it.isLetterOrDigit()) it else '-' }
    .joinToString("")
    .split('-')
    .filter { it.isNotEmpty() }
    .joinToString("-")
  return MANIFEST_FILE_PREFIX + slug.ifEmpty { "root" }
}

internal fun renderManifestLine(route: ManifestRoute): String =
  listOf(route.pattern, route.routeClassName, route.moduleId).joinToString(FIELD_SEPARATOR)

/** The route [line] records, or null when it is blank or does not carry all three fields. */
internal fun parseManifestLine(line: String): ManifestRoute? {
  if (line.isBlank()) return null
  val fields = line.split(FIELD_SEPARATOR).map { it.trim() }
  if (fields.size != 3 || fields.any { it.isEmpty() }) return null
  return ManifestRoute(pattern = fields[0], routeClassName = fields[1], moduleId = fields[2])
}
