plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  id("dev.carcara.perch.detekt")
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "com.example.sample.navigation"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
  }

  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      // `api` because `SampleRoute` extends `NavKey` in its own declaration.
      api(libs.navigation3.runtime)
    }
  }
}
