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

// A convention plugin: a module applies this to itself, mirroring `dev.carcara.perch.detekt` and
// `dev.carcara.perch.publishing`, rather than the root build reaching into subprojects
// (`subprojects { }`), which Isolated Projects forbids. See
// https://docs.gradle.org/current/userguide/isolated_projects.html
//
// Separate from `dev.carcara.perch.publishing` because being published is not the same thing as
// having an API worth guarding. Binary compatibility matters where somebody compiles against the
// artifact; a KSP processor and a Gradle plugin are loaded by their own runtimes from a
// ServiceLoader, so nothing is ever compiled against them and a dump of their entry point only
// costs a review round when it churns.

apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")
