plugins {
  // No `org.jetbrains.kotlin.android` alongside it: AGP 9 compiles Kotlin itself, and applying
  // that plugin on top of it is an error rather than a redundancy.
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
  // Compose Destinations generates its `NavGraphs` and `Direction`s from the `@Destination`
  // composables in this module. Perch's own processor does not run here - this module declares no
  // routes, it only consumes the ones sample-routes declares.
  alias(libs.plugins.ksp)
  id("dev.carcara.perch.detekt")
}

android {
  namespace = "com.example.sample.android"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.example.sample.android"
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
    targetSdk = libs.versions.android.compileSdk.get().toInt()
    versionCode = 1
    versionName = "0.1"
  }

  buildFeatures { compose = true }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(projects.sample.sampleApp)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.material3)
  implementation(libs.androidx.activity.compose)

  // One per demo screen. Nothing here is a Perch dependency: Perch hands back a route object and
  // has no opinion about which of these consumes it.
  implementation(libs.navigation3.runtime)
  implementation(libs.navigation3.ui)
  implementation(libs.voyager.navigator)
  implementation(libs.composeDestinations.core)
  ksp(libs.composeDestinations.ksp)
}
