plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.ksp)
  id("dev.carcara.perch")
  id("dev.carcara.perch.detekt")
}

kotlin {
  // Two targets, not one.
  //
  // Perch scans commonMain, and Kotlin Multiplatform only gives a module a commonMain compilation
  // once it declares two or more targets. With a single target there is nothing for the processor
  // to run on, so `dev.carcara.perch` rejects the module by name at configuration time.
  //
  // The requirement is on the module that declares routes. A module that only aggregates may have
  // a single target.
  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(projects.perchCore)
    }
  }
}

perch {
  outputPackage.set("com.example.sample.routes")
  // A project path rather than the `dev.carcara.perch:perch-ksp` convention, because the
  // processor lives in this build. A consumer outside this repository leaves it alone.
  processorCoordinates.set(projects.perchKsp.path)
}
