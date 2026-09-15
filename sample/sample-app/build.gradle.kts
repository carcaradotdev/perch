import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  id("dev.carcara.perch.aggregation")
  id("dev.carcara.perch.detekt")
}

// Everything this module re-exposes, named once because two unrelated rules land on the same
// four entries and drifting apart would break both quietly.
//
// They are `api` because all four show up in this module's own signatures: `sampleParser()`
// returns a `DeepLinkParser`, and what it parses to are the features' types, narrowed through
// `SampleRoute`. That is what lets `sample-android` navigate with one dependency on this module.
// It is also how the aggregator finds anything: it walks this module's own commonMain
// dependencies for published manifests, so a feature reachable only from a platform source set
// would be silently missing from `perchParser()`.
//
// And `export` is only honoured for an `api` dependency, so the framework's list can never be
// wider than this one anyway.
val exported = listOf(
  projects.perchCore,
  projects.sample.sampleNavigation,
  projects.sample.features.home.api,
  projects.sample.features.payments.api,
)

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

  iosSimulatorArm64 {
    // The framework the SwiftUI shell in `sample-ios-app` links against, and the iOS counterpart
    // of what `sample-android` gets by depending on this module directly. This is where it
    // belongs: producing the binary a native shell consumes is what the shared module of a Kotlin
    // Multiplatform app does, and a module of its own would only be this block plus a copy of the
    // `exported` list to keep in step with the one below.
    //
    // Debug only. Left to default, `binaries.framework` declares both build types, `assemble`
    // depends on each, and `./gradlew build` spends about two minutes linking a release framework
    // that nothing reads - `sample-ios-app` names the debug one.
    binaries.framework(listOf(NativeBuildType.DEBUG)) {
      baseName = "SampleShared"
      // Static, so the Swift app links one archive and there is no embedding step to get wrong.
      isStatic = true

      // A framework exports only what it is told to. Left out, a type still crosses but as an
      // opaque forward declaration Swift cannot name - so `parse` would come back and nothing
      // could be done with the result.
      exported.forEach(::export)
    }
  }

  sourceSets {
    commonMain.dependencies { exported.forEach(::api) }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

perchAggregation { outputPackage.set("com.example.sample.app") }
