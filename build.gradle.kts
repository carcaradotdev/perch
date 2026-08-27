plugins {
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.metro) apply false
}

subprojects {
  group = "dev.carcara.perch"
  version = "0.1.0-SNAPSHOT"

  apply(plugin = "io.gitlab.arturbosch.detekt")

  extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
  }
}
