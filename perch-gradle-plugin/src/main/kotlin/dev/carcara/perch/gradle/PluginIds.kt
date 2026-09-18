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

package dev.carcara.perch.gradle

/**
 * Applied-plugin id both Perch plugins test for. Kept as a string, and tested through
 * `pluginManager.hasPlugin`, because that is the only way to ask the question without naming a
 * Kotlin Gradle plugin type - see `PerchProducerPlugin.requireCommonMainCompilation`.
 */
internal const val KOTLIN_MULTIPLATFORM_ID: String = "org.jetbrains.kotlin.multiplatform"

/**
 * KSP's task on the `commonMain` metadata compilation, which is where Perch's processor runs. Both
 * the manifest artifact and the generated `perchModuleParser()` come out of it.
 */
internal const val KSP_METADATA_TASK: String = "kspCommonMainKotlinMetadata"
