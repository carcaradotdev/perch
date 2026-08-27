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
}

tasks.validatePlugins {
  enableStricterValidation = true
  failOnWarning = true
}
