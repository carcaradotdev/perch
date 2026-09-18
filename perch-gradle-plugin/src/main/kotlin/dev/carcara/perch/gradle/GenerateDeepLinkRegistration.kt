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

package dev.carcara.perch.gradle

import dev.carcara.perch.manifest.ManifestRoute
import dev.carcara.perch.manifest.parseManifestLine
import dev.carcara.perch.patternsConflict
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Generates `perchParser()` from every route manifest reachable from the applying module. */
@CacheableTask
public abstract class GenerateDeepLinkRegistration : DefaultTask() {

  /** Route manifests discovered through the applying module's own dependency graph. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val manifests: ConfigurableFileCollection

  /** Package the generated extension is emitted into. Required, and deliberately without a convention. */
  @get:Input
  public abstract val outputPackage: Property<String>

  /** Directory added to `commonMain`, holding the single generated file. */
  @get:OutputDirectory
  public abstract val outputDirectory: DirectoryProperty

  /**
   * What lenient resolution swallowed while looking for manifests. Reported, never an input: a
   * dependency that fails to resolve must not change the generated file, only explain itself.
   */
  @get:Internal
  public abstract val resolutionFailures: ListProperty<String>

  @TaskAction
  public fun generate() {
    val packageName = outputPackage.get()

    val manifestFiles = manifests.files.sortedBy { it.invariantSeparatorsPath }
    val routes = mutableListOf<DiscoveredRoute>()
    val unparseable = mutableListOf<String>()
    manifestFiles.forEach { file -> read(file, routes, unparseable) }

    // By route class, not by line: one module's manifest is reachable through more than one path
    // in the graph, so the same route arrives more than once and is not a collision with itself.
    val discovered = routes.distinctBy { it.route.routeClassName }
    failOnConflict(discovered)

    val registered = discovered.map { it.route.routeClassName }.sorted()
    write(packageName, registered)
    report(manifestFiles, registered, unparseable, packageName)
  }

  private fun read(file: File, routes: MutableList<DiscoveredRoute>, unparseable: MutableList<String>) {
    file.readLines().forEachIndexed { index, line ->
      if (line.isBlank()) return@forEachIndexed
      val route = parseManifestLine(line)
      if (route == null) {
        unparseable += "${file.name}:${index + 1}: $line"
      } else {
        routes += DiscoveredRoute(file, route)
      }
    }
  }

  /**
   * Fails the build when two routes could match the same URL.
   *
   * This is the only place the question can be asked. The processor sees one module at a time and
   * the parser sees them all, but only at runtime: without this, two feature modules declaring
   * `/payments/{id}` and `/payments/{code}` build green on every job, and the app throws
   * `DeepLinkCollisionException` the first time it builds a parser - a launch crash for a mistake
   * that belongs to whoever added the second route.
   *
   * Comparing every pair is quadratic in the number of routes an app declares. At the scale where
   * that costs anything the task is cached, and it runs once per aggregating module.
   */
  private fun failOnConflict(routes: List<DiscoveredRoute>) {
    for (i in routes.indices) {
      for (j in i + 1 until routes.size) {
        val left = routes[i]
        val right = routes[j]
        if (!patternsConflict(left.route.pattern, right.route.pattern)) continue
        throw GradleException(
          "Deep link collision between modules. '${left.route.routeClassName}' in " +
            "${left.route.moduleId} declares '${left.route.pattern}', and " +
            "'${right.route.routeClassName}' in ${right.route.moduleId} declares " +
            "'${right.route.pattern}'. A URL matching one matches the other, so the parser cannot " +
            "know which route to return. The manifests are ${left.file.name} and " +
            "${right.file.name}; change one of the two patterns.",
        )
      }
    }
  }

  /** A route and the manifest it was read from, which is what a collision message has to name. */
  private data class DiscoveredRoute(val file: File, val route: ManifestRoute)

  private fun write(packageName: String, routes: List<String>) {
    // Only the parser and the logger are imported: they are the ones named in the signature. A
    // route is written fully qualified on purpose: importing route types by simple name collides
    // the moment two modules declare `com.acme.a.Details` and `com.acme.b.Details`, and an
    // aggregator that spans every module in an app makes that ordinary rather than rare.
    val registrations = routes.joinToString("\n") { "  register<$it>()" }
    // Not `DeepLinkRegistration.kt`: that is what the KSP processor writes into a producer's own
    // outputPackage, and a module that both declares a route and aggregates - an app module with
    // one route in it - would otherwise get two files of that name in one package and a
    // duplicate-JVM-facade error naming neither Perch nor the reason.
    val outputFile = outputDirectory.get()
      .file(packageName.replace('.', '/') + "/PerchDeepLinkRegistration.kt")
      .asFile
    outputFile.parentFile.mkdirs()
    outputFile.writeText(
      """
      |package $packageName
      |
      |import dev.carcara.perch.DeepLinkLogger
      |import dev.carcara.perch.DeepLinkParser
      |
      |/**
      | * A parser with every deep-link route reachable from this module already registered.
      | * @generated by the generateDeepLinkRegistration Gradle task
      | */
      |public fun perchParser(
      |  schemes: Set<String>,
      |  hosts: Set<String> = emptySet(),
      |  logger: DeepLinkLogger = DeepLinkLogger.None,
      |): DeepLinkParser = DeepLinkParser(schemes, hosts, logger).apply {
      |$registrations
      |}
      |
      """.trimMargin(),
    )
  }

  /**
   * Manifests resolve leniently, so a dependency whose manifest failed to resolve and a dependency
   * that publishes no manifest at all produce the same empty result. Without this, the only symptom
   * of the first case is a deep link that never resolves at runtime, months later.
   */
  private fun report(
    manifestFiles: List<File>,
    routes: List<String>,
    unparseable: List<String>,
    packageName: String,
  ) {
    if (unparseable.isNotEmpty()) {
      logger.warn(
        "Perch: ignored ${unparseable.size} manifest line(s) that are not a " +
          "`pattern|routeClassName|moduleId` triple. Any route they meant to declare is missing " +
          "from $packageName.perchParser():\n" +
          unparseable.joinToString("\n") { "  - $it" },
      )
    }

    val failures = resolutionFailures.getOrElse(emptyList())
    if (failures.isNotEmpty()) {
      logger.warn(
        "Perch: could not resolve ${failures.size} of this module's dependencies while looking " +
          "for route manifests. Any route they declare is missing from " +
          "$packageName.perchParser():\n" +
          failures.joinToString("\n") { "  - $it" },
      )
    }

    val found = manifestFiles.joinToString("\n") { "  $it" }
    if (routes.isEmpty()) {
      logger.warn(
        "Perch: no deep-link routes were discovered for $packageName, so the generated " +
          "perchParser() registers nothing. Manifests are resolved leniently: a " +
          "dependency whose manifest failed to resolve is indistinguishable here from one that " +
          "publishes no manifest. Manifests found: ${manifestFiles.size}." +
          if (manifestFiles.isEmpty()) "" else "\n$found",
      )
    } else {
      logger.lifecycle(
        "Perch: generated perchParser() for $packageName with ${routes.size} route(s) " +
          "from ${manifestFiles.size} manifest(s):\n$found",
      )
    }
  }
}
