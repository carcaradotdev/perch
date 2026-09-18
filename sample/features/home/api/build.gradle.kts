/*
 * Copyright 2026 Carcara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKmpLibrary)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.ksp)
  id("dev.carcara.perch")
  id("dev.carcara.perch.detekt")
}

kotlin {
  explicitApi()

  androidLibrary {
    namespace = "com.example.sample.home.api"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
  }

  // Two targets minimum, because Perch scans commonMain and Kotlin Multiplatform only gives a
  // module a commonMain compilation once it declares two or more.
  jvm()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(projects.perchCore)
      api(projects.sample.sampleNavigation)
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }
  }
}

perch {
  outputPackage.set("com.example.sample.home.api")
  // A project path because the processor lives in this build; a consumer leaves this unset and
  // gets the published `dev.carcara.perch:perch-ksp` coordinate.
  processorCoordinates.set(projects.perchKsp.path)
}
