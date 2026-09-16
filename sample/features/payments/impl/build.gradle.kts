plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.metro)
  id("dev.carcara.perch.detekt")
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "com.example.sample.payments.impl"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
  }

  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      // Nothing in this module is public, so none of these is `api`. The graph module depends on
      // it for the contribution alone, and gets no types back.
      implementation(projects.sample.features.payments.api)
      implementation(projects.sample.features.home.api)
      implementation(projects.sample.sampleDi)
    }
  }
}

metro {
  // `ApprovalsHandler` is internal, so the graph module cannot see the class and cannot write the
  // provider for it. This makes this module write its own, which is the only thing that carries an
  // internal contribution across a module boundary. Without it the handler is dropped in silence:
  // the build stays green, the map arrives empty, and the link goes wherever it would have gone
  // with no gate at all.
  generateContributionProviders.set(true)
}
