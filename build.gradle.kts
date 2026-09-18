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
  alias(libs.plugins.kotlinMultiplatform) apply false
  alias(libs.plugins.kotlinSerialization) apply false
  alias(libs.plugins.androidKmpLibrary) apply false
  // Both Android plugins are named here even though only the sample app uses the application one.
  // They share a classpath, so a module asking for `com.android.application` with a version while
  // another module has already loaded AGP fails with "already on the classpath with an unknown
  // version". Declaring the version once, here, is what keeps the two in step.
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.composeCompiler) apply false
  alias(libs.plugins.ksp) apply false
}

// Nothing else belongs here. `subprojects { }` is cross-project access from root-project scope and
// Isolated Projects forbids it outright, so what would have gone in it lives in the convention
// plugins under `build-logic/` that each module applies to itself: `dev.carcara.perch.detekt` for
// linting, `dev.carcara.perch.publishing` for the coordinates, the POM and the Central Portal.
