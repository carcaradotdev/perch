plugins {
  alias(libs.plugins.kotlinMultiplatform)
  id("dev.carcara.perch.aggregation")
  id("dev.carcara.perch.detekt")
}

kotlin {
  // The same two targets sample-routes declares. Aggregation generates into commonMain, so every
  // target compiles the generated file, and a mismatch here would leave a target of this module
  // with no variant of sample-routes to resolve against.
  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(projects.perchCore)
      implementation(projects.sample.sampleRoutes)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.kotlinx.coroutines.test)
      implementation(projects.perchTest)
    }
  }
}

perchAggregation { outputPackage.set("com.example.sample.app") }
