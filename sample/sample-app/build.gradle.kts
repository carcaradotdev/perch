plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  id("dev.carcara.perch.aggregation")
  id("dev.carcara.perch.detekt")
}

kotlin {
  // The same targets sample-routes declares. Aggregation generates into commonMain, so every
  // target compiles the generated file, and a mismatch here would leave a target of this module
  // with no variant of sample-routes to resolve against.
  androidLibrary {
    namespace = "com.example.sample.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
  }

  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      // Both `api`, because both show up in this module's own signatures: `sampleParser()`
      // returns a `DeepLinkParser`, and what it parses to are sample-routes' types. That is what
      // lets `sample-android` navigate with one dependency on this module.
      api(projects.perchCore)
      api(projects.sample.sampleRoutes)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

perchAggregation { outputPackage.set("com.example.sample.app") }
