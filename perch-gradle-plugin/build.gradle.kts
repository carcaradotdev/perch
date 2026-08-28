plugins {
  `kotlin-dsl`
  `maven-publish`
}

group = "dev.carcara.perch"
version = "0.1.0-SNAPSHOT"

kotlin { jvmToolchain(21) }

dependencies {
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.ksp.gradlePlugin)
  testImplementation(libs.junit)
  testImplementation(gradleTestKit())
}

tasks.test {
  useJUnit()
  // GradleRunner spawns a real Gradle build per test; the default 512m is not enough.
  maxHeapSize = "2g"
  // The Kotlin Multiplatform fixture resolves the Kotlin plugin itself, so it needs the
  // catalog's version rather than a copy that can drift out of step with it.
  systemProperty("perch.kotlinVersion", libs.versions.kotlin.get())
  // PerchProducerPlugin reads KspExtension directly, and PerchAggregationPlugin reads
  // KotlinMultiplatformExtension, so a fixture that exercises either can't load it via
  // GradleRunner's withPluginClasspath(): that mechanism loads the plugin under test in a
  // classloader isolated from whatever loads the portal-resolved `com.google.devtools.ksp` or
  // `org.jetbrains.kotlin.multiplatform`, so the two disagree on what those extensions even are.
  // A fixture instead includes this build the way a real consumer does, via
  // `pluginManagement.includeBuild`, which shares one classloader graph across both plugins.
  systemProperty("perch.pluginBuildDir", project.projectDir.absolutePath)
}

tasks.validatePlugins {
  enableStricterValidation = true
  failOnWarning = true
}

gradlePlugin {
  plugins {
    create("perch") {
      id = "dev.carcara.perch"
      implementationClass = "dev.carcara.perch.gradle.PerchProducerPlugin"
    }
    create("perchAggregation") {
      id = "dev.carcara.perch.aggregation"
      implementationClass = "dev.carcara.perch.gradle.PerchAggregationPlugin"
    }
  }
}

// `kotlin-dsl` already applies `java-gradle-plugin`, which - once `maven-publish` is applied too -
// generates the main "pluginMaven" publication plus one marker publication per plugin declared
// above; the marker is what lets a consumer resolve `plugins { id("dev.carcara.perch") }` from
// this coordinate. This build has no version catalog plugin aliases to mirror (`com.vanniktech.
// maven.publish` targets Kotlin Multiplatform/Android publications, neither of which this
// JVM-only, single-module build has), so it configures the POM directly rather than through the
// `dev.carcara.perch.publishing` convention plugin used by the library modules.
publishing {
  publications.withType<MavenPublication>().configureEach {
    pom {
      name.set("perch-gradle-plugin")
      description.set("Gradle plugins for Perch, a type-safe deep-link library for Kotlin Multiplatform")
      url.set("https://github.com/carcaradotdev/perch")
      licenses {
        license {
          name.set("The Apache License, Version 2.0")
          url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
      }
      developers {
        developer {
          id.set("carcaradotdev")
          name.set("Carcara")
          url.set("https://github.com/carcaradotdev")
        }
      }
      scm {
        url.set("https://github.com/carcaradotdev/perch")
        connection.set("scm:git:git://github.com/carcaradotdev/perch.git")
        developerConnection.set("scm:git:ssh://git@github.com/carcaradotdev/perch.git")
      }
    }
  }
}
