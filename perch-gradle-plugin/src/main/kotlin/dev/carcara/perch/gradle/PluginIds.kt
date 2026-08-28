package dev.carcara.perch.gradle

/**
 * Applied-plugin id both Perch plugins test for. Kept as a string, and tested through
 * `pluginManager.hasPlugin`, because that is the only way to ask the question without naming a
 * Kotlin Gradle plugin type - see `PerchProducerPlugin.requireCommonMainCompilation`.
 */
internal const val KOTLIN_MULTIPLATFORM_ID: String = "org.jetbrains.kotlin.multiplatform"

/**
 * KSP's task on the `commonMain` metadata compilation, which is where Perch's processor runs. Both
 * the manifest artifact and the generated `registerDeepLinks()` come out of it.
 */
internal const val KSP_METADATA_TASK: String = "kspCommonMainKotlinMetadata"
