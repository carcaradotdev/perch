plugins {
  kotlin("jvm")
  id("dev.carcara.perch.detekt")
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
  testImplementation(libs.ktor.resources)
  testImplementation(projects.perchCore)
}

tasks.test { useJUnit() }
