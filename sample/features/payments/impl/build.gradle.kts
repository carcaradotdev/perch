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
