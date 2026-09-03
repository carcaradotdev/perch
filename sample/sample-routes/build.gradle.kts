plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.ksp)
  id("dev.carcara.perch")
  id("dev.carcara.perch.detekt")
}

kotlin {
  // More than one target, and that is a requirement rather than a preference.
  //
  // Perch scans commonMain, and Kotlin Multiplatform only gives a module a commonMain compilation
  // once it declares two or more targets. With a single target there is nothing for the processor
  // to run on, so `dev.carcara.perch` rejects the module by name at configuration time.
  //
  // The requirement is on the module that declares routes. A module that only aggregates may have
  // a single target.
  //
  // Android is here because `sample-android` consumes this module: without an Android variant
  // there is nothing for an Android consumer to resolve against.
  androidLibrary {
    namespace = "com.example.sample.routes"
    compileSdk = libs.versions.android.sampleCompileSdk.get().toInt()
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
  }

  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(projects.perchCore)
      // `api` because the routes implement `NavKey` in their own signatures. See SampleRoutes.kt
      // for why a route type may name a navigation library at all.
      api(libs.navigation3.runtime)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

perch {
  outputPackage.set("com.example.sample.routes")
  // A project path rather than the `dev.carcara.perch:perch-ksp:<version>` convention, because
  // the processor lives in this build. A consumer outside this repository leaves it alone.
  processorCoordinates.set(projects.perchKsp.path)
}
