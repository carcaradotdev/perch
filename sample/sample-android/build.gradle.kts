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
  // No `org.jetbrains.kotlin.android` alongside it: AGP 9 compiles Kotlin itself, and applying
  // that plugin on top of it is an error rather than a redundancy.
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeCompiler)
  id("dev.carcara.perch.detekt")
}

android {
  namespace = "com.example.sample.android"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "com.example.sample.android"
    minSdk = libs.versions.android.sampleMinSdk.get().toInt()
    targetSdk = libs.versions.android.compileSdk.get().toInt()
    versionCode = 1
    versionName = "0.1"
  }

  buildFeatures { compose = true }
}

kotlin { jvmToolchain(21) }

dependencies {
  // One dependency: sample-app `api`-exposes the parser, the shared route supertype and both
  // features' route types, so the app names the aggregator and gets everything it navigates to.
  implementation(projects.sample.sampleApp)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.material3)
  implementation(libs.androidx.activity.compose)

  // One per demo screen. Nothing here is a Perch dependency: Perch hands back a route object and
  // has no opinion about which of these consumes it. Both are Kotlin Multiplatform libraries,
  // which is the bar for appearing in this sample at all - a navigator that only ships an Android
  // artifact cannot demonstrate anything about a multiplatform route.
  implementation(libs.navigation3.runtime)
  implementation(libs.navigation3.ui)
  implementation(libs.voyager.navigator)
}
