plugins {
  kotlin("jvm")
  id("dev.carcara.perch.detekt")
  id("dev.carcara.perch.publishing")
}

kotlin {
  explicitApi()
  jvmToolchain(21)
}

dependencies {
  implementation(libs.ksp.api)
  testImplementation(libs.junit)
  testImplementation(libs.kctfork.core)
  testImplementation(libs.kctfork.ksp)
  // Only so one test can prove a Ktor `@Resource` class is not mistaken for a deep link. Nothing
  // Perch ships depends on Ktor.
  testImplementation(libs.ktor.resources)
  testImplementation(projects.perchCore)
}

tasks.test { useJUnit() }
