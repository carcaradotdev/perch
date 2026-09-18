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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

/**
 * The spike that established manifests can be discovered through a consumer's own dependency graph.
 *
 * Its `perchManifests` declares no attributes, and that shape is **not** the one
 * `PerchAggregationPlugin` ships. It resolves here only because both fixtures' producers are
 * `java-library`. Point the same attribute-free request at a Kotlin Multiplatform producer and it
 * stops working - outright for a native target, and only by accident for a JVM one, since such a
 * module publishes eight or more variants with nothing to choose between them. That is why the
 * plugin asks for the `commonMain` metadata attributes explicitly; read the comment on its
 * `perchManifests` attributes block before simplifying it back to what is written below.
 */
class ManifestDiscoveryTest {

  @get:Rule val projectDir = TemporaryFolder()

  private val kotlinVersion: String =
    checkNotNull(System.getProperty("perch.kotlinVersion")) {
      "Run these tests through Gradle: the Kotlin Multiplatform fixture needs the catalog's Kotlin version."
    }

  private fun file(path: String, content: String) {
    val f = File(projectDir.root, path)
    f.parentFile.mkdirs()
    f.writeText(content.trimIndent())
  }

  private fun producer(name: String, route: String) {
    file("$name/manifests/perch-manifest-$name.txt", "$route|com.acme.$name.Route|$name")
    file(
      "$name/build.gradle.kts",
      """
      plugins { `java-library` }
      configurations.consumable("perchManifestElements") {
        attributes { attribute(Attribute.of("dev.carcara.perch.manifest", String::class.java), "true") }
        outgoing.artifact(layout.projectDirectory.dir("manifests"))
      }
      """,
    )
  }

  @Test
  fun `consumer resolves manifests from its own project dependencies`() {
    producer("alpha", "/alpha/{id}")
    producer("beta", "/beta")

    file("settings.gradle.kts", """
      rootProject.name = "fixture"
      include(":alpha", ":beta", ":app")
    """)

    file(
      "app/build.gradle.kts",
      """
      plugins { `java-library` }

      dependencies {
        implementation(project(":alpha"))
        implementation(project(":beta"))
      }

      val manifests = configurations.resolvable("perchManifests") {
        extendsFrom(configurations.getByName("implementation"))
      }

      val view = manifests.get().incoming.artifactView {
        withVariantReselection()
        lenient(true)
        attributes { attribute(Attribute.of("dev.carcara.perch.manifest", String::class.java), "true") }
      }.files

      tasks.register("printManifests") {
        val resolved = view.asFileTree.matching { include("perch-manifest-*.txt") }
        inputs.files(resolved)
        doLast {
          resolved.files.sortedBy { it.name }.forEach { println("FOUND " + it.name) }
        }
      }
      """,
    )

    val result = GradleRunner.create()
      .withProjectDir(projectDir.root)
      .withArguments("printManifests", "--stacktrace")
      .withPluginClasspath()
      .build()

    assertTrue(result.output.contains("FOUND perch-manifest-alpha.txt"))
    assertTrue(result.output.contains("FOUND perch-manifest-beta.txt"))
  }

  @Test
  fun `consumer resolves manifests from a Kotlin Multiplatform commonMain graph`() {
    producer("alpha", "/alpha/{id}")
    producer("beta", "/beta")

    file(
      "settings.gradle.kts",
      """
      pluginManagement {
        repositories {
          gradlePluginPortal()
          mavenCentral()
        }
      }

      dependencyResolutionManagement {
        repositories { mavenCentral() }
      }

      rootProject.name = "fixture"
      include(":alpha", ":beta", ":app")
      """,
    )

    file(
      "app/build.gradle.kts",
      """
      plugins { kotlin("multiplatform") version "$kotlinVersion" }

      kotlin {
        jvm()
        sourceSets.commonMain.dependencies {
          implementation(project(":alpha"))
          api(project(":beta"))
        }
      }

      // commonMainImplementation does not extend commonMainApi, so an api(project(...))
      // dependency is invisible unless both buckets are extended.
      val manifests = configurations.resolvable("perchManifests") {
        extendsFrom(configurations.getByName("commonMainImplementation"))
        extendsFrom(configurations.getByName("commonMainApi"))
      }

      val view = manifests.get().incoming.artifactView {
        withVariantReselection()
        lenient(true)
        attributes { attribute(Attribute.of("dev.carcara.perch.manifest", String::class.java), "true") }
      }.files

      tasks.register("printManifests") {
        val resolved = view.asFileTree.matching { include("perch-manifest-*.txt") }
        inputs.files(resolved)
        doLast {
          resolved.files.sortedBy { it.name }.forEach { println("FOUND " + it.name) }
        }
      }
      """,
    )

    val result = GradleRunner.create()
      .withProjectDir(projectDir.root)
      .withArguments("printManifests", "--stacktrace")
      .withPluginClasspath()
      .build()

    assertTrue(result.output.contains("FOUND perch-manifest-alpha.txt"))
    assertTrue(result.output.contains("FOUND perch-manifest-beta.txt"))
  }
}
