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

// The convention plugin that publishes lives in `build-logic`, and this is a separate Gradle
// build from the root one, so it has to include it for itself. Nothing else here comes from
// there: the two Gradle plugins this build compiles are what the root build's `sample/` applies,
// which is the whole reason this is a separate build in the first place.
pluginManagement {
  includeBuild("../build-logic")
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
  versionCatalogs {
    create("libs") { from(files("../gradle/libs.versions.toml")) }
  }
}

rootProject.name = "perch-gradle-plugin"
