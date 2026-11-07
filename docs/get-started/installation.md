# Installation

## Requirements

- Gradle runs on JDK 21 or later. The Gradle plugin and the KSP processor are compiled for Java 21,
  so a Gradle daemon on an older JDK cannot load them.
- Each module that declares routes applies Kotlin Multiplatform, KSP and the kotlinx.serialization
  compiler plugin.
- The module that assembles your app applies Kotlin Multiplatform.

Perch 0.1.0 is built and tested with Kotlin 2.4.10 and KSP 2.3.10.

## Artifacts

All Perch artifacts are on Maven Central, under the group `dev.carcara.perch`.

| Artifact | Contents |
| --- | --- |
| `perch-core` | `@DeepLink`, `DeepLinkParser` and `DeepLinkLogger`. Kotlin Multiplatform. |
| `perch-ksp` | The KSP processor. The producer plugin adds it for you. |
| `perch-gradle-plugin` | The plugins `dev.carcara.perch` and `dev.carcara.perch.aggregation`. |

`perch-core` depends on `kotlinx-serialization-core` and on nothing else.

## 1. Add Maven Central to the plugin repositories

The Perch Gradle plugins are on Maven Central, not on the Gradle Plugin Portal. Add
`mavenCentral()` to `pluginManagement`. Keep `gradlePluginPortal()` for the Kotlin and KSP plugins.

```kotlin title="settings.gradle.kts"
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

## 2. Declare the plugins in the root build file

Declare every Gradle plugin that your modules apply in the root build file, with `apply false`. This
includes the Kotlin Gradle plugin.

=== "Kotlin DSL"

    ```kotlin title="build.gradle.kts"
    plugins {
        kotlin("multiplatform") version "2.4.10" apply false
        kotlin("plugin.serialization") version "2.4.10" apply false
        id("com.google.devtools.ksp") version "2.3.10" apply false
        id("dev.carcara.perch") version "0.1.0" apply false
        id("dev.carcara.perch.aggregation") version "0.1.0" apply false
    }
    ```

=== "Version catalog"

    ```toml title="gradle/libs.versions.toml"
    [versions]
    kotlin = "2.4.10"
    ksp = "2.3.10"
    perch = "0.1.0"

    [libraries]
    perch-core = { module = "dev.carcara.perch:perch-core", version.ref = "perch" }

    [plugins]
    kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
    kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
    ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
    perch = { id = "dev.carcara.perch", version.ref = "perch" }
    perch-aggregation = { id = "dev.carcara.perch.aggregation", version.ref = "perch" }
    ```

    ```kotlin title="build.gradle.kts"
    plugins {
        alias(libs.plugins.kotlin.multiplatform) apply false
        alias(libs.plugins.kotlin.serialization) apply false
        alias(libs.plugins.ksp) apply false
        alias(libs.plugins.perch) apply false
        alias(libs.plugins.perch.aggregation) apply false
    }
    ```

!!! warning "Declare the Kotlin plugin version only once"

    Declare each plugin version in the root build file, also for a plugin that only one module
    applies. If two modules each declare a version of the Kotlin plugin, Gradle loads the Kotlin
    plugin in two classloaders. The Apple targets then fail with a
    `SwiftPMLockTaskAggregationBuildService` cast exception. That message does not name Perch or
    the cause.

## 3. Apply the plugins to your modules

The module that declares routes applies the producer plugin. The module that assembles your app
applies the aggregator plugin. The [quick start](quick-start.md) shows both build files.
