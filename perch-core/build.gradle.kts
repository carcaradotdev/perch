plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.kotlinSerialization)
  id("dev.carcara.perch.detekt")
  id("dev.carcara.perch.publishing")
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "dev.carcara.perch"
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
      // Both `api` because both are on a public signature here: ktor-resources' ResourcesFormat
      // and kotlinx-serialization's KSerializer on `register`.
      //
      // kotlinx-serialization-core rather than -json: nothing here reads or writes JSON, and the
      // json artifact sat on every consumer's runtime and native compile classpath for nothing.
      api(libs.ktor.resources)
      api(libs.kotlinx.serialization.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}
