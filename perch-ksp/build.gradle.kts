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
  kotlin("jvm")
  id("dev.carcara.perch.detekt")
  id("dev.carcara.perch.publishing")
}

description = "The Perch KSP processor, which turns `@DeepLink` route classes into a registration file"

kotlin {
  explicitApi()
  jvmToolchain(21)

  // The path-pattern rules live in `shared/routing` and are compiled into this build from there.
  // Perch's three Gradle builds cannot depend on one another - the plugins arrive through
  // `pluginManagement.includeBuild` - and all three have to apply the same rule, so they share the
  // file rather than a copy of what it says. See the comment at the top of it.
  sourceSets.named("main") { kotlin.srcDir("../shared/routing") }

  // The manifest format lives in `shared/manifest` and is compiled into this build from there, so
  // the side that writes a manifest and the side that reads it cannot disagree about its shape.
  sourceSets.named("main") { kotlin.srcDir("../shared/manifest") }
}

dependencies {
  implementation(libs.ksp.api)
  testImplementation(libs.junit)
  testImplementation(libs.kctfork.core)
  testImplementation(libs.kctfork.ksp)
  // Only so one test can prove a Ktor `@Resource` class is not mistaken for a deep link. Nothing
  // Perch ships depends on Ktor.
  testImplementation(libs.ktor.resources)
  // So the processor's tests can check a generated pattern against the one the parser derives.
  testImplementation(projects.perchCore)
}

tasks.test { useJUnit() }
