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

class PerchProducerPluginTest {

  @get:Rule val projectDir = TemporaryFolder()

  // The build script injects these so a Kotlin or KSP bump moves one number in the version
  // catalogue rather than a dozen literals in here, which would otherwise keep passing against a
  // toolchain nobody ships any more.
  private val kotlinVersion: String =
    checkNotNull(System.getProperty("perch.kotlinVersion")) {
      "Run these tests through Gradle: the Kotlin Multiplatform fixtures need the catalog's Kotlin version."
    }

  private val kspVersion: String =
    checkNotNull(System.getProperty("perch.kspVersion")) {
      "Run these tests through Gradle: the fixtures resolve KSP with the catalog's version."
    }

  // What `perch.processorCoordinates` defaults to, so the assertion below pins the default against
  // the version this plugin build actually publishes.
  private val pluginVersion: String =
    checkNotNull(System.getProperty("perch.pluginVersion")) {
      "Run these tests through Gradle: the default processor coordinate carries this build's version."
    }

  // PerchProducerPlugin reads KspExtension directly, and calls it while KSP is applied. A
  // fixture that exercises that path can't load `dev.carcara.perch` via GradleRunner's
  // withPluginClasspath(): that mechanism runs the plugin under test in a classloader
  // isolated from whatever loads a portal-resolved `com.google.devtools.ksp`, so the two
  // disagree on what a `KspExtension` even is — a NoClassDefFoundError, not a test failure.
  // Every fixture below instead includes this build the way a real consumer does, via
  // `pluginManagement.includeBuild`, which puts both plugins in one classloader graph.
  private val pluginBuildDir: String =
    checkNotNull(System.getProperty("perch.pluginBuildDir")) {
      "Run these tests through Gradle: PerchProducerPlugin needs to be included as a build."
    }

  private fun file(path: String, content: String) {
    val f = File(projectDir.root, path)
    f.parentFile.mkdirs()
    f.writeText(content.trimIndent())
  }

  private fun settingsIncludingPerch(vararg includedProjects: String) = """
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
    ${includedProjects.joinToString("\n") { "include(\"$it\")" }}
  """

  private fun runner(vararg args: String) = GradleRunner.create()
    .withProjectDir(projectDir.root)
    .withArguments(*args, "--stacktrace")

  @Test
  fun `fails with a named message when KSP is not applied`() {
    file("settings.gradle.kts", settingsIncludingPerch())
    file(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
        id("dev.carcara.perch")
      }
      kotlin { jvm() }
      perch { outputPackage.set("com.acme.home") }
      """,
    )

    val result = runner("help").buildAndFail()

    assertTrue(result.output.contains("dev.carcara.perch: KSP is not configured"))
  }

  @Test
  fun `fails with a named message when outputPackage is unset`() {
    file("settings.gradle.kts", settingsIncludingPerch())
    // kspCommonMainKotlinMetadata only exists once a metadata compilation exists (which needs
    // two platform targets), and KSP skips it as NO-SOURCE without an actual commonMain file.
    file("src/commonMain/kotlin/Sample.kt", "package com.acme.home\n\nclass Sample")
    file(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
        id("com.google.devtools.ksp") version "$kspVersion"
        id("dev.carcara.perch")
      }
      kotlin {
        jvm()
        linuxX64()
      }
      // The real processor isn't published; standing in with a resolvable artifact keeps
      // dependency resolution from masking the outputPackage failure this test targets.
      perch { processorCoordinates.set("com.google.devtools.ksp:symbol-processing-api:$kspVersion") }
      """,
    )

    // --configuration-cache on this nested build (not the outer one running the test) runs the
    // outputPackage check under the mode every real consumer builds with. It does not, on its
    // own, prove the Provider's closure is free of a live `Project` capture — Gradle's own
    // "execution time value" optimisation for a plain `project.provider { }` evaluates that
    // closure once during the configuration-cache store step regardless of what it captures, so
    // the fix here is defensive/future-proofing rather than something this assertion can pin.
    val result = runner("kspCommonMainKotlinMetadata", "--configuration-cache").buildAndFail()

    assertTrue(result.output.contains("set `perch.outputPackage`"))
  }

  @Test
  fun `fails with a named message when the module declares a single target`() {
    file("settings.gradle.kts", settingsIncludingPerch())
    file("src/commonMain/kotlin/Sample.kt", "package com.acme.home\n\nclass Sample")
    file(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
        id("com.google.devtools.ksp") version "$kspVersion"
        id("dev.carcara.perch")
      }
      kotlin { jvm() }
      perch {
        outputPackage.set("com.acme.home")
        processorCoordinates.set("com.google.devtools.ksp:symbol-processing-api:$kspVersion")
      }
      """,
    )

    // `help` rather than a real task: the point is that this fails during configuration, before
    // Gradle ever goes looking for the `kspCommonMainKotlinMetadata` task that a single-target
    // module never gets. Failing here is what makes the message reach the person, instead of
    // Gradle's own "Task with name 'kspCommonMainKotlinMetadata' not found".
    val result = runner("help").buildAndFail()

    assertTrue(result.output.contains("dev.carcara.perch: :"))
    assertTrue(result.output.contains("declares 1 Kotlin target (jvm)"))
    assertTrue(result.output.contains("needs at least two"))
    // The cryptic failure this guard replaces must not be what the consumer sees.
    assertFalse(result.output.contains("Task with name 'kspCommonMainKotlinMetadata' not found"))
  }

  @Test
  fun `fails with a named message when Kotlin Multiplatform is not applied`() {
    file("settings.gradle.kts", settingsIncludingPerch())
    file(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
        id("com.google.devtools.ksp") version "$kspVersion"
        id("dev.carcara.perch")
      }
      perch { outputPackage.set("com.acme.home") }
      """,
    )

    val result = runner("help").buildAndFail()

    assertTrue(
      result.output.contains("dev.carcara.perch: Kotlin Multiplatform is not applied on :"),
    )
    // What `hasPlugin` buys over letting `getByType` throw. Without the check the consumer gets a
    // dump of every registered extension type, naming neither Perch nor the requirement.
    assertFalse(result.output.contains("Currently registered extension types"))
  }

  @Test
  fun `fails with a named message when no Kotlin plugin is applied at all`() {
    file("settings.gradle.kts", settingsIncludingPerch())
    file(
      "build.gradle.kts",
      """
      plugins {
        `java-library`
        id("dev.carcara.perch")
      }
      perch { outputPackage.set("com.acme.home") }
      """,
    )

    val result = runner("help").buildAndFail()

    // The KSP check wins this race, and that is the right outcome: with no Kotlin plugin there is
    // no KSP either, and "apply KSP" is the first thing this module is missing. Pinned because it
    // is also what makes the Kotlin Multiplatform check below it unreachable without a Kotlin
    // plugin on the classpath - the reason that check does not need to defend against the Kotlin
    // Gradle plugin classes being absent.
    assertTrue(result.output.contains("dev.carcara.perch: KSP is not configured on :"))
    assertFalse(result.output.contains("NoClassDefFoundError"))
  }

  @Test
  fun `registers a consumable manifest configuration`() {
    file("settings.gradle.kts", settingsIncludingPerch())
    file(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
        id("com.google.devtools.ksp") version "$kspVersion"
        id("dev.carcara.perch")
      }
      // Two targets, because Perch requires them: one target gets no commonMain compilation.
      kotlin {
        jvm()
        linuxX64()
      }
      perch { outputPackage.set("com.acme.home") }

      tasks.register("printConfig") {
        val names = configurations.names.toList()
        doLast { if ("perchManifestElements" in names) println("CONFIG PRESENT") }
      }
      """,
    )

    val result = runner("printConfig", "--configuration-cache").build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":printConfig")?.outcome)
    assertTrue(result.output.contains("CONFIG PRESENT"))
  }

  @Test
  fun `resolves a project-path processor coordinate to a project dependency`() {
    file("settings.gradle.kts", settingsIncludingPerch(":proc"))
    file("proc/build.gradle.kts", """plugins { `java-library` }""")
    file(
      "build.gradle.kts",
      """
      import org.gradle.api.artifacts.ProjectDependency

      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
        id("com.google.devtools.ksp") version "$kspVersion"
        id("dev.carcara.perch")
      }
      kotlin {
        jvm()
        linuxX64()
      }
      perch {
        outputPackage.set("com.acme.home")
        processorCoordinates.set(":proc")
      }

      tasks.register("printProcessorDependency") {
        // Resolved here, inside the task's own configuration lambda — same as the brief's
        // "registers a consumable manifest configuration" fixture. A top-level script `val`
        // read from `doLast` needs the script instance itself to reach it, which configuration
        // cache refuses to serialize as a "Gradle script object reference"; a value local to
        // this lambda is instead captured directly, with no such reference required.
        val dependencies = configurations.getByName("kspCommonMainMetadata").dependencies
        val isProjectDependency = dependencies.any { it is ProjectDependency && it.path == ":proc" }
        doLast { println("PROJECT_DEPENDENCY=${'$'}isProjectDependency") }
      }
      """,
    )

    val result = runner("printProcessorDependency", "--configuration-cache").build()

    assertTrue(result.output.contains("PROJECT_DEPENDENCY=true"))
  }

  @Test
  fun `the default processor coordinate carries this build's version`() {
    file("settings.gradle.kts", settingsIncludingPerch())
    file(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
        id("com.google.devtools.ksp") version "$kspVersion"
        id("dev.carcara.perch")
      }
      kotlin {
        jvm()
        linuxX64()
      }
      // No `processorCoordinates` line: the default is the thing under test.
      perch { outputPackage.set("com.acme.home") }

      tasks.register("printProcessorCoordinate") {
        val coordinates = configurations.getByName("kspCommonMainMetadata").dependencies
          .map { "${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
        doLast { coordinates.forEach { println("PROCESSOR=${'$'}it") } }
      }
      """,
    )

    val result = runner("printProcessorCoordinate", "--configuration-cache").build()

    // A coordinate with no version resolves against nothing, and fails with a message naming
    // neither Perch nor `perch.processorCoordinates`. The version is generated into the plugin jar
    // rather than left to every consumer to pin.
    assertTrue(result.output.contains("PROCESSOR=dev.carcara.perch:perch-ksp:$pluginVersion"))
  }
}
