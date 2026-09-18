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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

class PerchAggregationPluginTest {

  @get:Rule val projectDir = TemporaryFolder()

  private val kotlinVersion: String =
    checkNotNull(System.getProperty("perch.kotlinVersion")) {
      "Run these tests through Gradle: the Kotlin Multiplatform fixture needs the catalog's Kotlin version."
    }

  // PerchAggregationPlugin reads KotlinMultiplatformExtension directly, so a fixture cannot load
  // it through GradleRunner's withPluginClasspath(): that mechanism runs the plugin under test in
  // a classloader isolated from the one loading the portal-resolved Kotlin Multiplatform plugin,
  // and the two then disagree on what a `KotlinMultiplatformExtension` even is. Including this
  // build the way a real consumer does puts both plugins in one classloader graph.
  private val pluginBuildDir: String =
    checkNotNull(System.getProperty("perch.pluginBuildDir")) {
      "Run these tests through Gradle: PerchAggregationPlugin needs to be included as a build."
    }

  private fun file(path: String, content: String) {
    val f = File(projectDir.root, path)
    f.parentFile.mkdirs()
    f.writeText(content.trimIndent())
  }

  /**
   * Settings plus a root build script, the way a real multi-module repository is laid out. Both
   * plugins are resolved once at the root with `apply false` rather than versioned in each module:
   * a module applying `kotlin.multiplatform` alone and a module applying it alongside
   * `dev.carcara.perch.aggregation` otherwise get different script classpaths, which loads the
   * Kotlin Gradle plugin into two classloaders and makes its Apple-target shared build services
   * fail an internal cast.
   */
  private fun fixture(vararg projects: String) {
    val includes = projects.joinToString("\n") { "include(\":$it\")" }
    file(
      "settings.gradle.kts",
      """
      pluginManagement {
        includeBuild("$pluginBuildDir")
        repositories {
          gradlePluginPortal()
          mavenCentral()
        }
      }

      dependencyResolutionManagement {
        repositories { mavenCentral() }
      }

      rootProject.name = "fixture"
      $includes
      """,
    )
    file(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion" apply false
        id("dev.carcara.perch.aggregation") apply false
      }
      """,
    )
  }

  /**
   * A module that publishes [lines] on `perchManifestElements` by hand. Standing in for the KSP
   * producer keeps the aggregator's own tests independent of a processor round.
   */
  private fun producer(name: String, vararg lines: String, targets: String = "jvm()") {
    file("$name/manifests/perch-manifest-$name.txt", lines.joinToString("\n"))
    file(
      "$name/build.gradle.kts",
      """
      plugins { id("org.jetbrains.kotlin.multiplatform") }
      kotlin { $targets }
      configurations.consumable("perchManifestElements") {
        attributes {
          attribute(Attribute.of("${PERCH_MANIFEST_ATTRIBUTE.name}", String::class.java), "true")
        }
        outgoing.artifact(layout.projectDirectory.dir("manifests"))
      }
      """,
    )
  }

  private fun implementation(producer: String) = "implementation(project(\":$producer\"))"

  private fun api(producer: String) = "api(project(\":$producer\"))"

  private fun consumer(
    vararg dependencies: String,
    targets: String = "jvm()",
    outputPackage: String? = "com.acme.app",
    extraScript: String = "",
  ) {
    val declared = dependencies.joinToString("\n") { "    $it" }
    val aggregation =
      if (outputPackage == null) "" else "perchAggregation { outputPackage.set(\"$outputPackage\") }"
    file(
      "app/build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform")
        id("dev.carcara.perch.aggregation")
      }

      kotlin {
        $targets
        sourceSets.commonMain.dependencies {
      $declared
        }
      }

      $aggregation
      $extraScript
      """,
    )
  }

  // Not DeepLinkRegistration.kt: the KSP processor already writes a file of that name into a
  // producer's own outputPackage, and a module that both declares a route and aggregates would end
  // up with two of them in one package.
  private fun generated(): File = File(
    projectDir.root,
    "app/build/generated/perch/commonMain/kotlin/com/acme/app/PerchDeepLinkRegistration.kt",
  )

  // Every nested build runs with the configuration cache on, which is how every real consumer
  // builds; a task that only works with it off is a task that does not work.
  private fun runner(vararg args: String) = GradleRunner.create()
    .withProjectDir(projectDir.root)
    .withArguments(*args, "--configuration-cache", "--stacktrace")

  @Test
  fun `generates an empty function when no producer is reachable`() {
    fixture("app")
    consumer()

    val result = runner(":app:generateDeepLinkRegistration").build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":app:generateDeepLinkRegistration")?.outcome)
    val text = generated().readText()
    assertTrue(text, text.contains("public fun perchParser("))
    assertFalse(text.contains("register<"))
    // The fail-open path is indistinguishable from an empty graph, so it has to say so out loud.
    assertTrue(result.output.contains("no deep-link routes were discovered"))
  }

  @Test
  fun `aggregates fully qualified routes across the api and implementation buckets`() {
    fixture("alpha", "beta", "app")
    // Same simple name, two packages: the case that makes importing by simple name generate code
    // that does not compile.
    producer("alpha", "/alpha/{id}|com.acme.alpha.Details|com.acme.alpha")
    producer("beta", "/beta|com.acme.beta.Details|com.acme.beta")
    // commonMainImplementation and commonMainApi are sibling buckets - neither extends the other -
    // so a producer reached through `api(project(...))` is invisible unless both are extended.
    consumer(implementation("alpha"), api("beta"))

    val result = runner(":app:generateDeepLinkRegistration").build()

    val text = generated().readText()
    assertTrue(text, text.contains("register<com.acme.alpha.Details>()"))
    assertTrue(text, text.contains("register<com.acme.beta.Details>()"))
    assertFalse(text, text.contains("import com.acme.alpha.Details"))
    assertFalse(text, text.contains("import com.acme.beta.Details"))
    assertTrue(result.output.contains("2 route(s) from 2 manifest(s)"))
  }

  @Test
  fun `aggregates routes for a consumer that targets only iOS`() {
    fixture("alpha", "app")
    producer(
      "alpha",
      "/alpha/{id}|com.acme.alpha.AlphaLink|com.acme.alpha",
      targets = "iosSimulatorArm64()",
    )
    consumer(implementation("alpha"), targets = "iosSimulatorArm64()")

    runner(":app:generateDeepLinkRegistration").build()

    val text = generated().readText()
    assertTrue(text, text.contains("register<com.acme.alpha.AlphaLink>()"))
  }

  @Test
  fun `deduplicates a route named by two manifests`() {
    fixture("alpha", "beta", "app")
    producer("alpha", "/shared|com.acme.shared.SharedLink|com.acme.alpha")
    producer("beta", "/shared|com.acme.shared.SharedLink|com.acme.beta")
    consumer(implementation("alpha"), implementation("beta"))

    runner(":app:generateDeepLinkRegistration").build()

    val text = generated().readText()
    assertEquals(
      text,
      1,
      Regex("register<com\\.acme\\.shared\\.SharedLink>\\(\\)").findAll(text).count(),
    )
  }

  @Test
  fun `a dependency with no manifest variant does not fail the build`() {
    fixture("plain", "app")
    file(
      "plain/build.gradle.kts",
      """
      plugins { id("org.jetbrains.kotlin.multiplatform") }
      kotlin { jvm() }
      """,
    )
    consumer(implementation("plain"))

    val result = runner(":app:generateDeepLinkRegistration").build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":app:generateDeepLinkRegistration")?.outcome)
  }

  @Test
  fun `names the dependency it could not resolve rather than reporting nothing`() {
    fixture("unbuildable", "app")
    // A module with no plugins publishes nothing consumable, so resolving it fails. Lenient
    // resolution is what keeps that from failing the build, and is also what would otherwise turn
    // it into a route that silently never resolves at runtime, with nothing to read anywhere.
    file("unbuildable/build.gradle.kts", "")
    consumer(implementation("unbuildable"))

    val result = runner(":app:generateDeepLinkRegistration").build()

    assertTrue(
      result.output,
      result.output.contains("could not resolve 1 of this module's dependencies"),
    )
    assertTrue(result.output, result.output.contains("Could not resolve project ':unbuildable'"))
  }

  @Test
  fun `names a manifest line it could not parse`() {
    fixture("alpha", "app")
    producer(
      "alpha",
      "/alpha|com.acme.alpha.AlphaLink|com.acme.alpha",
      "this line has no fields at all",
    )
    consumer(implementation("alpha"))

    val result = runner(":app:generateDeepLinkRegistration").build()

    assertTrue(result.output, result.output.contains("ignored 1 manifest line(s)"))
    assertTrue(result.output, result.output.contains("perch-manifest-alpha.txt:2"))
    // The good line alongside the bad one still registers.
    assertTrue(generated().readText().contains("register<com.acme.alpha.AlphaLink>()"))
  }

  @Test
  fun `regenerates when a manifest changes`() {
    fixture("alpha", "app")
    producer("alpha", "/alpha|com.acme.alpha.AlphaLink|com.acme.alpha")
    consumer(implementation("alpha"))

    runner(":app:generateDeepLinkRegistration").build()
    assertTrue(generated().readText().contains("register<com.acme.alpha.AlphaLink>()"))

    File(projectDir.root, "alpha/manifests/perch-manifest-alpha.txt")
      .writeText("/gamma|com.acme.alpha.GammaLink|com.acme.alpha")

    val second = runner(":app:generateDeepLinkRegistration").build()

    assertEquals(TaskOutcome.SUCCESS, second.task(":app:generateDeepLinkRegistration")?.outcome)
    assertTrue(generated().readText().contains("register<com.acme.alpha.GammaLink>()"))
  }

  @Test
  fun `is up to date on a rerun with no change`() {
    fixture("alpha", "app")
    producer("alpha", "/alpha|com.acme.alpha.AlphaLink|com.acme.alpha")
    consumer(implementation("alpha"))

    runner(":app:generateDeepLinkRegistration").build()
    val second = runner(":app:generateDeepLinkRegistration").build()

    assertEquals(TaskOutcome.UP_TO_DATE, second.task(":app:generateDeepLinkRegistration")?.outcome)
  }

  @Test
  fun `runs with the configuration cache enabled`() {
    fixture("alpha", "app")
    producer("alpha", "/alpha|com.acme.alpha.AlphaLink|com.acme.alpha")
    consumer(implementation("alpha"))

    val first = runner(":app:generateDeepLinkRegistration").build()
    assertEquals(TaskOutcome.SUCCESS, first.task(":app:generateDeepLinkRegistration")?.outcome)
    assertTrue(first.output.contains("Configuration cache entry stored"))

    // Storing an entry only proves the state was serializable; reusing it proves the task runs
    // from that state, with no `Project` to fall back on.
    File(projectDir.root, "alpha/manifests/perch-manifest-alpha.txt")
      .writeText("/gamma|com.acme.alpha.GammaLink|com.acme.alpha")
    val second = runner(":app:generateDeepLinkRegistration").build()

    assertTrue(second.output, second.output.contains("Reusing configuration cache"))
    assertEquals(TaskOutcome.SUCCESS, second.task(":app:generateDeepLinkRegistration")?.outcome)
    assertTrue(generated().readText().contains("register<com.acme.alpha.GammaLink>()"))
  }

  @Test
  fun `discovers manifests with isolated projects enabled`() {
    fixture("alpha", "app")
    producer("alpha", "/alpha|com.acme.alpha.AlphaLink|com.acme.alpha")
    consumer(implementation("alpha"))

    val result = runner(":app:generateDeepLinkRegistration", ISOLATED_PROJECTS).build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":app:generateDeepLinkRegistration")?.outcome)
    assertTrue(generated().readText().contains("register<com.acme.alpha.AlphaLink>()"))
  }

  @Test
  fun `the isolated projects flag is genuinely in effect`() {
    // A control with no Perch in it at all: `subprojects { group = ... }` is cross-project access
    // from root-project scope, the violation Isolated Projects exists to reject. If the flag below
    // were the silent no-op that the un-prefixed `org.gradle.isolated-projects` name is on Gradle
    // 9.6.1, this build would pass with the flag exactly as it does without it, and the run above
    // would be proving nothing.
    fixture("other")
    file("other/build.gradle.kts", "")
    file("build.gradle.kts", """subprojects { group = "cross-project-write" }""")

    val without = runner("help").build()
    val with = runner("help", ISOLATED_PROJECTS).buildAndFail()

    assertTrue(without.output, without.output.contains("BUILD SUCCESSFUL"))
    assertTrue(
      with.output,
      with.output.contains("cannot access 'Project.group' functionality on subprojects"),
    )
  }

  @Test
  fun `fails with a named message when Kotlin Multiplatform is not applied`() {
    fixture("app")
    file(
      "app/build.gradle.kts",
      """
      plugins { id("dev.carcara.perch.aggregation") }
      perchAggregation { outputPackage.set("com.acme.app") }
      """,
    )

    val result = runner("help").buildAndFail()

    assertTrue(
      result.output,
      result.output.contains("dev.carcara.perch.aggregation: Kotlin Multiplatform is not applied"),
    )
  }

  @Test
  fun `fails naming the property when outputPackage is unset`() {
    fixture("app")
    consumer(outputPackage = null)

    val result = runner(":app:generateDeepLinkRegistration").buildAndFail()

    assertTrue(result.output, result.output.contains("outputPackage"))
    assertTrue(result.output, result.output.contains("generateDeepLinkRegistration"))
  }

  private companion object {
    // Gradle 9.6.1 still gates Isolated Projects behind the incubating `unsafe` name; the
    // documented `org.gradle.isolated-projects` spelling does nothing at all here, and
    // `--isolated-projects` is not a recognised CLI flag yet.
    const val ISOLATED_PROJECTS = "-Dorg.gradle.unsafe.isolated-projects=true"
  }
}
