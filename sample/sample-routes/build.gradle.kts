plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.ksp)
  id("dev.carcara.perch")
  id("dev.carcara.perch.detekt")
}

kotlin {
  // Two targets, not one. A single-target Kotlin Multiplatform module gets no metadata
  // compilation, so KSP registers no `kspCommonMainKotlinMetadata` task, and the manifest
  // artifact `dev.carcara.perch` declares `builtBy` that task fails to wire up. Every real
  // consumer of a multiplatform deep-link library has more than one target anyway.
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
