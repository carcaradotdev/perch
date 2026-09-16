import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.metro)
  id("dev.carcara.perch.aggregation")
  id("dev.carcara.perch.detekt")
}

// One list, read twice below. `api` because each of these appears in this module's own signatures,
// and
// because the aggregator walks this module's commonMain dependencies for published manifests - a
// feature reachable only from a platform source set would be missing from `perchParser()`.
// `export` is only honoured for an `api` dependency, so the two can never legally differ.
//
// `sample-di` is here for the second reason only: it publishes no manifest and adds nothing to
// `perchParser()`. It is in this module's signatures - `SampleGraph` names its types - and Swift
// has to be able to name them too.
val exported = listOf(
  projects.perchCore,
  projects.sample.sampleNavigation,
  projects.sample.sampleDi,
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
    // What `sample-ios-app` links against. Debug only: left to default this declares both build
    // types and `assemble` depends on each, costing about two minutes linking a release framework
    // nothing reads.
    binaries.framework(listOf(NativeBuildType.DEBUG)) {
      baseName = "SampleShared"
      isStatic = true
      // Without this a type still crosses, but as a forward declaration Swift cannot name.
      exported.forEach(::export)
    }
  }

  sourceSets {
    commonMain.dependencies {
      exported.forEach(::api)

      // Not exported and not `api`: this module is here so its contributed handler is on the
      // compile classpath when the graph is generated, which is the only way the graph learns of
      // it. Nothing in this module names a type from it.
      implementation(projects.sample.features.payments.impl)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

perchAggregation { outputPackage.set("com.example.sample.app") }
