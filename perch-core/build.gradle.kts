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
      // Every one of these is `api` because every one of them is on a public signature here:
      // ktor-resources' ResourcesFormat, kotlinx-serialization's KSerializer on `register`, and
      // coroutines' StateFlow, CoroutineScope and CoroutineDispatcher across DeepLinkManager.
      // Coroutines behind `implementation` compiled only because ktor-resources happens to
      // api-expose it, which is a third party's choice to reverse at any time.
      //
      // kotlinx-serialization-core rather than -json: nothing here reads or writes JSON, and the
      // json artifact sat on every consumer's runtime and native compile classpath for nothing.
      api(libs.ktor.resources)
      api(libs.kotlinx.serialization.core)
      api(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(projects.perchTest)
    }
  }
}
