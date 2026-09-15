plugins {
  alias(libs.plugins.kotlinMultiplatform)
  id("dev.carcara.perch.detekt")
}

kotlin {
  explicitApi()

  // Only the simulator target, because that is the only one the modules below it declare. Adding
  // iosArm64 here would not fail at configuration - it would fail at resolution, with no variant
  // of `sample-app` to match against.
  iosSimulatorArm64 {
    binaries.framework {
      baseName = "SampleShared"
      // Static, so the Swift app links one archive and there is no embedding step to get wrong.
      isStatic = true

      // A framework exports only what it is told to. Left out, a type still crosses if it is
      // reachable, but as an opaque forward declaration the Swift side cannot name - so the
      // parser would come back and nothing could be done with it.
      export(projects.sample.sampleApp)
      export(projects.sample.features.home.api)
      export(projects.sample.features.payments.api)
      export(projects.sample.sampleNavigation)
      export(projects.perchCore)
    }
  }

  sourceSets {
    commonMain.dependencies {
      // `api`, not `implementation`: `export` above is only honoured for an api dependency.
      api(projects.sample.sampleApp)
      api(projects.sample.features.home.api)
      api(projects.sample.features.payments.api)
      api(projects.sample.sampleNavigation)
      api(projects.perchCore)
    }
  }
}
