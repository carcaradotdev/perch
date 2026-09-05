plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  id("dev.carcara.perch.aggregation")
  id("dev.carcara.perch.detekt")
}

kotlin {
  // The same targets the feature modules declare. Aggregation generates into commonMain, so every
  // target compiles the generated file, and a mismatch here would leave a target of this module
  // with no variant of a feature to resolve against.
  androidLibrary {
    namespace = "com.example.sample.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
  }

  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      // All `api`, because all three show up in this module's own signatures: `sampleParser()`
      // returns a `DeepLinkParser`, and what it parses to are the features' types, narrowed
      // through `SampleRoute`. That is what lets `sample-android` navigate with one dependency
      // on this module.
      //
      // They are also how the aggregator finds anything: it walks this module's own commonMain
      // dependencies for published manifests, so a feature reachable only from a platform source
      // set would be silently missing from `perchParser()`.
      api(projects.perchCore)
      api(projects.sample.sampleNavigation)
      api(projects.sample.features.home.api)
      api(projects.sample.features.payments.api)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

perchAggregation { outputPackage.set("com.example.sample.app") }
