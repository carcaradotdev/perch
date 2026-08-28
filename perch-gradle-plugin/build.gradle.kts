plugins {
  `kotlin-dsl`
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
