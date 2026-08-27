plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.metro)
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "dev.carcara.perch.metro"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }

  jvm()
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  macosX64()
  macosArm64()

  sourceSets {
    commonMain.dependencies {
      api(projects.perchCore)
      implementation(libs.kotlinx.coroutines.core)
    }
  }
}
