plugins {
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  // Both Android plugins are named here even though only the sample app uses the application one.
  // They share a classpath, so a module asking for `com.android.application` with a version while
  // another module has already loaded AGP fails with "already on the classpath with an unknown
  // version". Declaring the version once, here, is what keeps the two in step.
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.ksp) apply false
}

// Per-module group and version are set from `settings.gradle.kts`'s
// `gradle.lifecycle.beforeProject { }`; detekt's application, its extension configuration and the
// `check` -> `Detekt` task wiring live in the `dev.carcara.perch.detekt` convention plugin (see
// `build-logic/`). `subprojects { }` is cross-project access from root-project scope, and
// Isolated Projects forbids it outright.
