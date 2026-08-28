plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  alias(libs.plugins.ksp) apply false
}

// Per-module group and version are set from `settings.gradle.kts`'s
// `gradle.lifecycle.beforeProject { }`; detekt's application, its extension configuration and the
// `check` -> `Detekt` task wiring live in the `dev.carcara.perch.detekt` convention plugin (see
// `build-logic/`). `subprojects { }` is cross-project access from root-project scope, and
// Isolated Projects forbids it outright.
