# Get started

This page sets up two modules:

- `my-routes` declares the routes. It applies the producer plugin.
- `app` assembles the app. It applies the aggregator plugin and creates the parser.

??? info "Requirements"

    - Gradle runs on JDK 21 or later. The Perch plugin and KSP processor are compiled for Java 21.
    - The routes module applies Kotlin Multiplatform, KSP and the kotlinx.serialization compiler
      plugin, and has two or more Kotlin targets.
    - The app module applies Kotlin Multiplatform.

    Perch 0.1.0 is built and tested with Kotlin 2.4.10 and KSP 2.3.10. `perch-core` depends only on
    `kotlinx-serialization-core`.

## 1. Add the repositories

The Perch Gradle plugins are on Maven Central, not on the Gradle Plugin Portal:

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

Declare every plugin version once, in the root build file, with `apply false`:

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

??? warning "Why the root build file"

    If two modules each declare a version of the Kotlin plugin, Gradle loads it in two classloaders.
    The Apple targets then fail with a `SwiftPMLockTaskAggregationBuildService` cast exception that
    does not name Perch or the cause.

## 3. Declare routes

Apply the producer plugin to the module that declares your routes:

```kotlin title="my-routes/build.gradle.kts"
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("dev.carcara.perch")
}

kotlin {
    jvm()               // The producer plugin needs at least two targets.
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("dev.carcara.perch:perch-core:0.1.0")
        }
    }
}

perch {
    outputPackage.set("com.example.routes")
}
```

Then annotate classes in `commonMain` with `@DeepLink`:

```kotlin title="my-routes/src/commonMain/kotlin/com/example/routes/Routes.kt"
@DeepLink("/home")
data object HomeLink

@DeepLink("/payments/{id}")
data class PaymentLink(val id: String)
```

A route needs no interface and no `@Serializable`. `@DeepLink` is `@MetaSerializable`, so the
serialization plugin generates the serializer.

## 4. Aggregate the routes

Apply the aggregator plugin to the module that assembles your app, and depend on the routes module
from `commonMain`:

```kotlin title="app/build.gradle.kts"
plugins {
    kotlin("multiplatform")
    id("dev.carcara.perch.aggregation")
}

kotlin {
    jvm()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":my-routes"))
        }
    }
}

perchAggregation {
    outputPackage.set("com.example.app")
}
```

Gradle now generates `perchParser()` into `commonMain` of `app`, before it compiles that source set.

??? example "The generated code"

    ```kotlin title="build/generated/perch/commonMain/kotlin/com/example/app/PerchDeepLinkRegistration.kt"
    package com.example.app

    import dev.carcara.perch.DeepLinkLogger
    import dev.carcara.perch.DeepLinkParser

    public fun perchParser(
      schemes: Set<String>,
      hosts: Set<String> = emptySet(),
      logger: DeepLinkLogger = DeepLinkLogger.None,
    ): DeepLinkParser = DeepLinkParser(schemes, hosts, logger).apply {
      register<com.example.routes.HomeLink>()
      register<com.example.routes.PaymentLink>()
    }
    ```

## 5. Parse a URL

```kotlin
val parser = perchParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))

when (val route = parser.parse("myapp://payments/abc123")) {
    is PaymentLink -> println("Open payment ${route.id}")
    HomeLink -> println("Open home")
    else -> println("Not a Perch route")
}
```

`parse` returns the route object, or `null` if no route matches. Because `schemes` contains
`https`, `hosts` is necessary. See [Schemes and hosts](guides/schemes-and-hosts.md).

!!! tip "One module only"

    If your routes and the code that parses URLs are in the same module, skip step 4. The producer
    plugin also generates `perchModuleParser()`, which only that module can call:

    ```kotlin
    val parser = perchModuleParser(schemes = setOf("myapp"))
    ```

## Next steps

- [Declare routes](guides/routes.md) with optional, query and nested parameters.
- [Hand the route to a navigator](guides/navigation.md).
