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
  `kotlin-dsl`
}

kotlin { jvmToolchain(21) }

dependencies {
  // A precompiled script plugin that applies detekt and configures `DetektExtension` needs
  // detekt's own Gradle plugin classes on this build's classpath, both to resolve `apply(plugin
  // = "io.gitlab.arturbosch.detekt")` and to compile the type references in that script.
  implementation(libs.detekt.gradlePlugin)
  // Same reasoning for the publishing convention plugin: it references
  // `MavenPublishBaseExtension` and applies both plugins by id, so both need to be on this
  // build's own classpath.
  implementation(libs.mavenPublish.gradlePlugin)
  implementation(libs.binaryCompatibilityValidator.gradlePlugin)
}
