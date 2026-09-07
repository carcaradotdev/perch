plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.ksp)
  id("dev.carcara.perch")
  id("dev.carcara.perch.detekt")
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "com.example.sample.home.api"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
  }

  // Two targets minimum, because Perch scans commonMain and Kotlin Multiplatform only gives a
  // module a commonMain compilation once it declares two or more.
  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(projects.perchCore)
      api(projects.sample.sampleNavigation)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

perch {
  outputPackage.set("com.example.sample.home.api")
  // A project path because the processor lives in this build; a consumer leaves this unset and
  // gets the published `dev.carcara.perch:perch-ksp` coordinate.
  processorCoordinates.set(projects.perchKsp.path)
}
