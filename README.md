# Perch

Perch is a type-safe deep-link library for Kotlin Multiplatform. Annotate a route class, apply two
Gradle plugins, and Perch generates the code that maps a URL to that class — or to `null`, if
nothing matches. What to do with the typed target it hands back — whether and how to navigate — is
your app's decision, not Perch's.

## Installation

Perch is not yet on Maven Central (see [Status](#status) below); for now, publish it to your local
Maven repository from a checkout of this repository and consume it from there:

```bash
git clone https://github.com/carcaradotdev/perch
cd perch
./gradlew publishToMavenLocal
./gradlew -p perch-gradle-plugin publishToMavenLocal
```

Then, in your app:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal() // until Perch is on Maven Central
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal() // until Perch is on Maven Central
        mavenCentral()
    }
}
```

Declare every Gradle plugin your modules will apply once, at the root, with `apply false` —
including the Kotlin Gradle plugin itself:

```kotlin
// build.gradle.kts (root)
plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("dev.carcara.perch") version "0.1.0-SNAPSHOT" apply false
    id("dev.carcara.perch.aggregation") version "0.1.0-SNAPSHOT" apply false
}
```

Do this even if only one module needs a given plugin. Two modules that each declare
`id("org.jetbrains.kotlin.multiplatform") version "..."` independently — one applying a Perch
plugin, one not — load the Kotlin Gradle plugin into two different classloaders, and the Apple
targets' shared build service then fails with a `SwiftPMLockTaskAggregationBuildService` cast
exception that names neither Perch nor the real cause. Declaring the Kotlin Gradle plugin and both
Perch plugins once at the root and `apply false` everywhere else — so every module resolves the
same plugin instance — avoids it.

The library coordinates:

| Artifact | Contains |
| --- | --- |
| `dev.carcara.perch:perch-core` | `DeepLinkTarget`, `DeepLinkParser`, `DeepLinkLogger` |
| `dev.carcara.perch:perch-ksp` | The KSP processor; `dev.carcara.perch` adds it for you, so nothing in your build names it |

`perch-core` is Kotlin Multiplatform. `perch-ksp` is a plain Kotlin/JVM module, not Multiplatform at
all — KSP processors run on the JVM regardless of the targets of the module they process, so it
does not need to be. Both are version `0.1.0-SNAPSHOT`.

## Quick start

This walks through the same thing `sample/` in this repository builds and tests end to end; the
snippets below are taken from it.

### 1. Declare a route

In the module that owns your route definitions, apply Kotlin Multiplatform, KSP, kotlinx.serialization
and Perch's producer plugin (versions come from the root `build.gradle.kts` above, so none are
repeated here):

```kotlin
// my-routes/build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("dev.carcara.perch")
}

kotlin {
    // Perch's producer plugin needs at least two targets — see "The codegen pipeline" below.
    jvm()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("dev.carcara.perch:perch-core:0.1.0-SNAPSHOT")
        }
    }
}

perch {
    outputPackage.set("com.example.routes")
}
```

Then declare routes as classes annotated `@Resource` that implement `DeepLinkTarget`:

```kotlin
package com.example.routes

import dev.carcara.perch.DeepLinkTarget
import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/home")
class HomeLink : DeepLinkTarget

@Serializable
@Resource("/payments/{id}")
class PaymentLink(val id: String) : DeepLinkTarget
```

That is the whole KSP contract: **a class annotated `@Resource` that implements `DeepLinkTarget`,
in the sources of a module applying `dev.carcara.perch`, becomes a registered route.** Nothing
else registers it, and nothing outside that module's own sources is scanned.

### 2. Aggregate them

In the module that assembles your app, apply Perch's aggregation plugin instead:

```kotlin
// app/build.gradle.kts
plugins {
    kotlin("multiplatform") // version from the root build.gradle.kts
    id("dev.carcara.perch.aggregation")
}

kotlin {
    jvm()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("dev.carcara.perch:perch-core:0.1.0-SNAPSHOT")
            implementation(project(":my-routes"))
        }
    }
}

perchAggregation {
    outputPackage.set("com.example.app")
}
```

This generates a `registerAllDeepLinks()` extension on `DeepLinkParser` into `commonMain`,
gathering every route reachable from this module's own `commonMain` dependencies:

```kotlin
package com.example.app

import dev.carcara.perch.DeepLinkParser

/**
 * Registers every deep-link route reachable from this module.
 * @generated by the generateDeepLinkRegistration Gradle task
 */
public fun DeepLinkParser.registerAllDeepLinks() {
  register<com.example.routes.HomeLink>()
  register<com.example.routes.PaymentLink>()
}
```

### 3. Build a parser and resolve a link

```kotlin
import dev.carcara.perch.DeepLinkParser

fun appParser(): DeepLinkParser =
    DeepLinkParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))
        .apply { registerAllDeepLinks() }
```

```kotlin
when (val target = appParser().parse("myapp://payments/abc123")) {
    is PaymentLink -> println("Navigate to payment ${target.id}")
    is HomeLink -> println("Navigate home")
    null -> println("Not a Perch route")
}
```

That is the whole surface: a typed target, or `null`. Deciding when to act on it, how to navigate,
and whether the user is allowed to land there is your app's own logic, sitting on top of whatever
navigation library you already use.

## The codegen pipeline

Two Gradle plugins, applied to different modules:

- **`dev.carcara.perch`** — the *producer* plugin. Apply it to a module that declares routes. It
  runs the Perch KSP processor over that module's own `commonMain` sources, publishes the routes
  it finds on a `perchManifestElements` configuration for an aggregator to pick up, and generates
  an `internal fun DeepLinkParser.registerDeepLinks()` into that module's `commonMain`. A
  single-module app needs no aggregator at all: apply this plugin, and call `registerDeepLinks()`
  on your own parser.
- **`dev.carcara.perch.aggregation`** — the *aggregator* plugin. Apply it to the module that
  assembles your app (or any module that wants a single `registerAllDeepLinks()` covering several
  producers). It walks this module's own `commonMain` dependency graph, collects every manifest it
  can reach, and generates the registration function.

A module can apply either, both, or neither — the sample's `sample-routes` applies only the
producer plugin and `sample-app` applies only the aggregator, which is the common shape.

Two properties of this pipeline are worth knowing before you hit them as a mystery:

**A module applying the producer plugin needs at least two Kotlin targets.** Perch's processor
runs on the shared `commonMain` compilation, and Kotlin Multiplatform only creates that
compilation once a module declares two or more targets — with a single target there is no common
compilation for the processor to scan, so a single-target module cannot declare Perch routes at
all. This is a constraint on the module applying `dev.carcara.perch`, not on your app: the module
that aggregates with `dev.carcara.perch.aggregation` has no such requirement and may have a single
target. In practice this costs nothing, because a module holding the shared route definitions of a
multiplatform app already builds for more than one platform. Applying the producer plugin to a
single-target module fails at configuration time with a message naming the module and its target
count, rather than failing later with an opaque missing-task error.

**Manifest discovery covers `commonMain` only.** The aggregator resolves through
`commonMainImplementation` and `commonMainApi`; a producer dependency declared only in a platform
source set — `androidMain`, `iosMain` — or through `commonMainCompileOnly` is invisible to it. This
is not an oversight: the generated registration is emitted into `commonMain`, so a route class
reachable only from `androidMain` could not be referenced by a `commonMain` `register<T>()` call
even if discovery found it. The failure mode is silent — the deep link just never resolves, with no
error anywhere — so if a route is missing from `registerAllDeepLinks()`, check which source set its
module depends on the producer from.

## Schemes, hosts, and why hosts are required for `http`/`https`

`DeepLinkParser` takes a set of `schemes` and, optionally, a set of `hosts`:

```kotlin
DeepLinkParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))
```

**If `schemes` contains `http` or `https`, `hosts` must be non-empty, or the constructor throws.**
This is the library's one security property, not an arbitrary validation rule: `http` and `https`
are schemes any website can use to link into your app (via App Links / Universal Links), so without
a host check, any site on the internet could mint a link that resolves to a route your app owns.
Requiring `hosts` for those two schemes forces you to name the domains you actually control. A
custom scheme (`myapp://...`) needs no hosts — only your own app can register that scheme with the
OS, so there is no equivalent look-alike risk, and `hosts` is not consulted for it even if you
supply one.

## Status

`perch-core` and `perch-ksp` publish to `mavenLocal()`, alongside both Gradle plugin markers, and
both library modules are under a binary-compatibility (`apiCheck`/`apiDump`) guard. Maven Central
publishing is not yet wired up — it needs a Sonatype Central Portal account, a verified
`dev.carcara` namespace, and a GPG key, on top of the publishing already in place.

## Contributing

There are three separate Gradle builds in this repository: the root build, `perch-gradle-plugin`
(the two Gradle plugins), and `build-logic` (the convention plugins the other two apply). A change
that touches the plugin build needs its own invocation, since its tasks are not reachable from the
root build's task graph:

```bash
./gradlew build                                                  # root build: library, KSP processor, sample
./gradlew -p perch-gradle-plugin build --no-configuration-cache  # the two Gradle plugins
./gradlew -p build-logic build                                   # the convention plugins
```

`./gradlew build` runs `check`, which runs `apiCheck` for every published module and detekt over
every main source set — fails if you've broken binary compatibility without running `apiDump`, or
introduced a lint violation. Test sources are not linted. It also builds and tests `sample/`,
which exercises the whole KSP and aggregation pipeline end to end; if a change to either plugin
breaks the pipeline, the sample is what notices.

Nothing runs them for you: there is no CI yet. Run all three before opening a pull request, on
macOS — the Apple targets do not build on other platforms.
