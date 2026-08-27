package dev.carcara.perch.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

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
