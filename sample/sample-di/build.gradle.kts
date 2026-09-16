plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.metro)
  id("dev.carcara.perch.detekt")
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "com.example.sample.di"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
  }

  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(projects.perchCore)
      api(projects.sample.sampleNavigation)
    }
  }
}
