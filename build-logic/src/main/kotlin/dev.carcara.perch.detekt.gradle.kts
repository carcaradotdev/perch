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

// A convention plugin: each module applies this to itself, rather than the root build reaching
// into subprojects (`subprojects { }`), which Isolated Projects forbids. See
// https://docs.gradle.org/current/userguide/isolated_projects.html

apply(plugin = "io.gitlab.arturbosch.detekt")

extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
  // `rootDir` is this project's own (immutable) view of the build's root directory - unlike
  // `rootProject.file(...)`, reading it is not a cross-project access.
  config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  parallel = true
}

// The detekt Gradle plugin creates one `Detekt` task per Kotlin source set/target on a
// multiplatform module (detektMetadataCommonMain, detektJvmMain, detektAndroidMain, ...), but the
// plain `detekt` lifecycle task it also registers depends on none of them, so it is NO-SOURCE for
// a Kotlin Multiplatform module and `check` inherits that blind spot. Wire `check` to the live
// collection of per-source-set tasks instead: `tasks.matching` and `tasks.withType` are both lazy
// and updated as tasks are registered, so this keeps working as later modules add their own
// source sets and targets without editing this file.
tasks.matching { it.name == "check" }.configureEach {
  dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
}
