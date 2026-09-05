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

  // Three targets, and at least two of them are a requirement rather than a preference: Perch
  // scans commonMain, and Kotlin Multiplatform only gives a module a commonMain compilation once
  // it declares two or more targets.
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
  // A project path rather than the `dev.carcara.perch:perch-ksp:<version>` convention, because
  // the processor lives in this build. A consumer outside this repository leaves it alone.
  processorCoordinates.set(projects.perchKsp.path)
}
