package dev.carcara.perch.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

class PerchProducerPluginTest {

  @get:Rule val projectDir = TemporaryFolder()

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
        id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
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
        id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
        id("com.google.devtools.ksp") version "2.3.10"
        id("dev.carcara.perch")
      }
      kotlin {
        jvm()
        linuxX64()
      }
      // The real processor isn't published; standing in with a resolvable artifact keeps
      // dependency resolution from masking the outputPackage failure this test targets.
      perch { processorCoordinates.set("com.google.devtools.ksp:symbol-processing-api:2.3.10") }
      """,
    )

    val result = runner("kspCommonMainKotlinMetadata").buildAndFail()

    assertTrue(result.output.contains("set `perch.outputPackage`"))
  }

  @Test
  fun `registers a consumable manifest configuration`() {
    file("settings.gradle.kts", settingsIncludingPerch())
    file(
      "build.gradle.kts",
      """
      plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
        id("com.google.devtools.ksp") version "2.3.10"
        id("dev.carcara.perch")
      }
      kotlin { jvm() }
      perch { outputPackage.set("com.acme.home") }

      tasks.register("printConfig") {
        val names = configurations.names.toList()
        doLast { if ("perchManifestElements" in names) println("CONFIG PRESENT") }
      }
      """,
    )

    val result = runner("printConfig").build()

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
        id("org.jetbrains.kotlin.multiplatform") version "2.4.10"
        id("com.google.devtools.ksp") version "2.3.10"
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
        doLast {
          val dependencies = configurations.getByName("kspCommonMainMetadata").dependencies
          val isProjectDependency = dependencies.any { it is ProjectDependency && it.path == ":proc" }
          println("PROJECT_DEPENDENCY=${'$'}isProjectDependency")
        }
      }
      """,
    )

    val result = runner("printProcessorDependency").build()

    assertTrue(result.output.contains("PROJECT_DEPENDENCY=true"))
  }
}
