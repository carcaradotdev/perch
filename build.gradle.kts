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

  // The detekt Gradle plugin creates one `Detekt` task per Kotlin source set/target on a
  // multiplatform module (detektMetadataCommonMain, detektJvmMain, detektAndroidMain, ...),
  // but the plain `detekt` lifecycle task it also registers depends on none of them, so it
  // is NO-SOURCE for a Kotlin Multiplatform module and `check` inherits that blind spot.
  // Wire `check` to the live collection of per-source-set tasks instead: `tasks.matching`
  // and `tasks.withType` are both lazy and updated as tasks are registered, so this keeps
  // working as later modules add their own source sets and targets without editing this file.
  tasks.matching { it.name == "check" }.configureEach {
    dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
  }
}
