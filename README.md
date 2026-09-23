# Perch

Perch is a type-safe deep-link library for Kotlin Multiplatform. Annotate a route class, apply two
Gradle plugins, and Perch generates the code that turns a URL into that class, or into `null` when
nothing matches. What you do with the object it hands back is your app's decision.

## Installation

Both Gradle plugins are on Maven Central rather than the Gradle Plugin Portal, so `mavenCentral()`
goes in `pluginManagement` next to `gradlePluginPortal()`, which stays for the Kotlin and KSP
plugins.

```kotlin
// settings.gradle.kts
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

Declare every Gradle plugin your modules apply once, at the root, with `apply false`. That includes
the Kotlin Gradle plugin itself:

```kotlin
// build.gradle.kts (root)
plugins {
    kotlin("multiplatform") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
    id("dev.carcara.perch") version "0.1.0" apply false
    id("dev.carcara.perch.aggregation") version "0.1.0" apply false
}
```

Do this even when only one module needs a given plugin. Two modules that each declare
`id("org.jetbrains.kotlin.multiplatform") version "..."` themselves, one applying a Perch plugin and
one not, load the Kotlin Gradle plugin into two classloaders. The Apple targets' shared build service
then fails with a `SwiftPMLockTaskAggregationBuildService` cast exception that names neither Perch
nor the real cause.

The library coordinates:

| Artifact | Contains |
| --- | --- |
| `dev.carcara.perch:perch-core` | `@DeepLink`, `DeepLinkParser`, `DeepLinkLogger` |
| `dev.carcara.perch:perch-ksp` | The KSP processor; `dev.carcara.perch` adds it for you, so nothing in your build names it |

`perch-core` is Kotlin Multiplatform and depends on `kotlinx-serialization-core` and nothing else.
`perch-ksp` is a plain Kotlin/JVM module, because KSP processors run on the JVM whatever targets the
module they process has. Both are version `0.1.0`.

## Quick start

`sample/` in this repository builds and tests all of this end to end. It is laid out the way an app
is:

```
sample/
  sample-navigation/            SampleRoute, the supertype the app narrows to
  features/home/api/            @DeepLink("/home")                    ← producer
  features/payments/api/        @DeepLink("/payments/{id}") + one more ← producer
  features/payments/impl/       the handler that gates the approvals link
  sample-di/                    the router, and the seams the shells fill
  sample-app/                   perchParser() over both features       ← aggregator
  sample-android/               one activity, three demos
  sample-ios-app/               SwiftUI, on the same parsed objects
```

Two producers rather than one is the point of that shape. It is the only way the aggregation step
does something a single module could not do for itself.

### 1. Declare a route

In the module that owns your route definitions, apply Kotlin Multiplatform, KSP,
kotlinx.serialization and Perch's producer plugin. Versions come from the root build file:

```kotlin
// my-routes/build.gradle.kts
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("dev.carcara.perch")
}

kotlin {
    // Perch's producer plugin needs at least two targets. See "The codegen pipeline" below.
    jvm()
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

Then declare routes as classes annotated `@DeepLink`:

```kotlin
package com.example.routes

import dev.carcara.perch.DeepLink

@DeepLink("/home")
class HomeLink

@DeepLink("/payments/{id}")
class PaymentLink(val id: String)
```

The annotation is the whole thing. A route implements no interface of Perch's, and needs no
`@Serializable` either: `@DeepLink` is `@MetaSerializable`, so the kotlinx.serialization compiler
plugin generates the serialiser from it alone.

That is the whole KSP contract: **a class annotated `@DeepLink`, in the sources of a module applying
`dev.carcara.perch`, becomes a registered route.** Nothing else registers one, and nothing outside
that module's own sources is scanned. Ktor's `@Resource` classes are HTTP resources, not deep links,
and Perch reads `@DeepLink` only, so the two never collide.

### 2. Aggregate them

In the module that assembles your app, apply the aggregation plugin instead:

```kotlin
// app/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("dev.carcara.perch.aggregation")
}

kotlin {
    jvm()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("dev.carcara.perch:perch-core:0.1.0")
            implementation(project(":my-routes"))
        }
    }
}

perchAggregation {
    outputPackage.set("com.example.app")
}
```

This generates a `perchParser()` factory into `commonMain`, carrying every route reachable from this
module's own `commonMain` dependencies:

```kotlin
package com.example.app

import dev.carcara.perch.DeepLinkLogger
import dev.carcara.perch.DeepLinkParser

/**
 * A parser with every deep-link route reachable from this module already registered.
 * @generated by the generateDeepLinkRegistration Gradle task
 */
public fun perchParser(
  schemes: Set<String>,
  hosts: Set<String> = emptySet(),
  logger: DeepLinkLogger = DeepLinkLogger.None,
): DeepLinkParser = DeepLinkParser(schemes, hosts, logger).apply {
  register<com.example.routes.HomeLink>()
  register<com.example.routes.PaymentLink>()
}
```

A parser and its routes arrive together, so there is no half-built state to forget about. If one
does happen, a hand-built `DeepLinkParser` nobody registered anything on, the first `parse` says so
through the `DeepLinkLogger` instead of quietly answering `null` forever.

### 3. Build a parser and resolve a link

```kotlin
import com.example.app.perchParser
import dev.carcara.perch.DeepLinkParser

fun appParser(): DeepLinkParser =
    perchParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))
```

It is a plain function, so it drops into whatever DI you use, a Metro or Dagger `@Provides` or a
Koin `single { }`, without Perch knowing anything about it:

```kotlin
@Provides
@SingleIn(AppScope::class)
fun deepLinkParser(logger: DeepLinkLogger): DeepLinkParser =
    perchParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"), logger = logger)
```

```kotlin
when (val target = appParser().parse("myapp://payments/abc123")) {
    is PaymentLink -> println("Navigate to payment ${target.id}")
    is HomeLink -> println("Navigate home")
    else -> println("Not a Perch route")
}
```

`parse` returns `Any?`, because Perch does not decide what your routes have in common. Give your
routes a supertype of your own and you narrow once, after which everything is typed and "not one of
ours" is a single branch:

```kotlin
interface Route

@DeepLink("/home")
class HomeLink : Route

when (val target = appParser().parse(url) as? Route) {
    is PaymentLink -> ...
    is HomeLink -> ...
    else -> ...
}
```

Seal that type and the `when` loses its `else` too, but only if every route lives in the module that
declares the supertype, because Kotlin permits implementations of a sealed type nowhere else. An app
that splits routes across feature modules, the layout the sample uses, gets the narrowing and not the
exhaustiveness.

That is the whole surface: a route object, or `null`. When to act on it, how to navigate, and
whether the user is allowed to land there is your app's own logic, sitting on top of whatever
navigation library you already use.

## Handing the route to a navigator

`sample/sample-android` is an installable app that takes one parsed route and gives it to two
navigation libraries in turn, one screen each:

```bash
./gradlew :sample:sample-android:installDebug
adb shell am start -a android.intent.action.VIEW -d "sample://payments/abc123"
```

The URL is also editable in the app, so both demos are reachable without `adb`.

Both are Kotlin Multiplatform libraries, which is the bar for being in this sample at all: Perch
turns one URL into one route object for every target, so a navigator that only ships an Android
artifact has nothing to say about that.

**Navigation 3** needs no adapter. Its back stack holds `NavKey`, a marker interface, and because
Perch demands no supertype the route classes are free to implement it, so `parse` returns an object
the back stack already accepts:

```kotlin
@DeepLink("/payments/{id}")
class PaymentLink(val id: String) : NavKey

val route = parser.parse(url)
if (route is NavKey) backStack.add(route)
```

`NavKey` asks that keys be serializable so `rememberNavBackStack` can restore them, which `@DeepLink`
has already arranged: it is `@MetaSerializable`, so the compiler generates the serializer without a
second annotation.

**Voyager** needs a second type. A `Screen` declares `@Composable fun Content()`, so it *is* the UI,
and a shared route module implementing it would have to depend on Compose and carry the layout:

```kotlin
fun Any?.toScreen(): Screen? = when (this) {
    is HomeLink -> HomeScreen
    is PaymentLink -> PaymentScreen(id)
    else -> null
}
```

**Through a DI graph** is how an app of any size will reach the parser, and Perch occupies one
provider in it:

```kotlin
@BindingContainer
@ContributesTo(AppScope::class)
object DeepLinkBindings {
    @Provides
    fun provideParser(): DeepLinkParser = appParser()
}
```

From there nothing downstream names a feature, or knows a generator was involved. `sample-di` holds a
router that injects that parser alongside a map of per-route handlers, and `features/payments/impl`
contributes one handler into that map, so a feature declares the links it owns and what happens when
one is opened, and neither needs a line in a central list. `sample-ios-app` implements the router's
navigation seam in Swift, which is the same graph reached from the other side.

Some navigators bring a deep-link feature of their own. It does not overlap with this one: Perch
decides what a URL means while it is still a URL, and hands over a typed object; what happens to that
object is the navigator's business.

## The codegen pipeline

Two Gradle plugins, applied to different modules:

- **`dev.carcara.perch`**, the *producer* plugin. Apply it to a module that declares routes. It runs
  the Perch KSP processor over that module's own `commonMain` sources, publishes the routes it finds
  on a `perchManifestElements` configuration for an aggregator to pick up, and generates an
  `internal fun perchModuleParser()` into that module's `commonMain`. A single-module app needs no
  aggregator at all: apply this plugin and call `perchModuleParser()`. Set `outputPackage` to a blank
  string and the module publishes its routes without generating a parser of its own.
- **`dev.carcara.perch.aggregation`**, the *aggregator* plugin. Apply it to the module that assembles
  your app, or any module that wants a single `perchParser()` covering several producers. It walks
  that module's own `commonMain` dependency graph, collects every manifest it can reach, and
  generates the registration function. It is also where a collision between two modules is caught:
  the producer plugin sees one module at a time, so two features that each declare `/payments/{id}`
  only meet here.

A module can apply either, both, or neither. Each of the sample's two feature modules applies only
the producer plugin and `sample-app` applies only the aggregator, which is the common shape.

**A module applying the producer plugin needs at least two Kotlin targets.** Perch's processor runs
on the shared `commonMain` compilation, and Kotlin Multiplatform only creates that compilation once a
module declares two or more targets. With a single target there is nothing for the processor to scan.
This binds the module applying `dev.carcara.perch`, not your app: the module that aggregates has no
such requirement and may have a single target. In practice it costs nothing, because a module holding
the shared route definitions of a multiplatform app already builds for more than one platform.
Applying the producer plugin to a single-target module fails at configuration time with a message
naming the module and its target count, rather than failing later with an opaque missing-task error.

**Manifest discovery covers `commonMain` only.** The aggregator resolves through
`commonMainImplementation` and `commonMainApi`. A producer dependency declared only in a platform
source set, `androidMain` or `iosMain`, or through `commonMainCompileOnly`, is invisible to it. That
is deliberate: the generated registration is emitted into `commonMain`, so a route class reachable
only from `androidMain` could not be referenced by a `commonMain` `register<T>()` call even if
discovery found it. The failure mode is silent, the deep link just never resolves, so if a route is
missing from `perchParser()`, check which source set its module depends on the producer from.

## Schemes, hosts, and why hosts are required for `http`/`https`

`DeepLinkParser` takes a set of `schemes` and, optionally, a set of `hosts`:

```kotlin
DeepLinkParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))
```

**If `schemes` contains `http` or `https`, `hosts` must be non-empty, or the constructor throws.**
This is the library's one security property, not an arbitrary validation rule. Any website can link
into your app over `http` and `https` via App Links and Universal Links, so without a host check any
site on the internet could mint a link that resolves to a route your app owns. Requiring `hosts` for
those two schemes forces you to name the domains you control. A custom scheme (`myapp://...`) needs
no hosts, since only your own app can register that scheme with the OS, and `hosts` is not consulted
for it even if you supply one.

## Status

Perch ships three artifacts: `perch-core`, `perch-ksp` and `perch-gradle-plugin`, the last alongside
both plugin markers. Each carries the sources jar, javadoc jar and complete POM Maven Central
requires, and all three take their coordinates, licence, developer and SCM from one convention
plugin, so a release cannot describe one of them differently from the others. `perch-core` is under a
binary-compatibility (`apiCheck`/`apiDump`) guard; the processor and the plugin are not, because
nothing is ever compiled against them.

A release is a published GitHub Release whose tag is the version. The tag is the only place that
number lives: `.github/workflows/release.yml` passes it to both builds as
`ORG_GRADLE_PROJECT_version`, so there is no version bump commit and no way for the tag and the
artifacts to disagree. It needs four secrets from the repository: `MAVEN_CENTRAL_USERNAME` and
`MAVEN_CENTRAL_PASSWORD`, which are a Central Portal user token rather than an account login, and
`SIGNING_IN_MEMORY_KEY` with `SIGNING_IN_MEMORY_KEY_PASSWORD` for the GPG key.

The workflow uploads and stops. Perch ships from two separate Gradle builds, so the Portal receives
two deployments and no single upload is the whole release. Both sit staged at
[central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments)
until someone releases them together or drops both. Automatic release would mean a failure in the
second upload leaving the first already public and immutable.

## Contributing

There are three separate Gradle builds in this repository: the root build, `perch-gradle-plugin` (the
two Gradle plugins), and `build-logic` (the convention plugins the other two apply). A change that
touches the plugin build needs its own invocation, since its tasks are not reachable from the root
build's task graph:

```bash
./gradlew build                                                  # root build: library, KSP processor, sample
./gradlew -p perch-gradle-plugin build --no-configuration-cache  # the two Gradle plugins
./gradlew -p build-logic build                                   # the convention plugins
```

`./gradlew build` runs `check`, which runs `apiCheck` for `perch-core` and detekt over every main
source set, so it fails if you have broken binary compatibility without running `apiDump` or
introduced a lint violation. Test sources are not linted. It also builds and tests `sample/`, which
exercises the whole KSP and aggregation pipeline end to end; if a change to either plugin breaks the
pipeline, the sample is what notices.

CI runs all three on every pull request, in two jobs: `plugins` builds and tests `build-logic` and
`perch-gradle-plugin` on Linux, and `library` runs the root build on macOS, the only host that can
compile the Apple targets. Both finish by publishing to `mavenLocal()`, so a break in the POM or in
the sources and javadoc jars fails a pull request rather than a release.

Running them yourself first is still faster than waiting, and needs macOS for the same reason.

## License

Apache License 2.0. See [LICENSE](LICENSE).

The resource serialisation in `perch-core` is derived from
[Ktor](https://github.com/ktorio/ktor)'s `ktor-resources`, also under Apache 2.0.
[NOTICE](NOTICE) names every derived file and the Ktor source it comes from.

Every source file carries the header, and `scripts/license_header.py` is what puts it there:

```
python3 scripts/license_header.py check    # names the files that are missing it or stale
python3 scripts/license_header.py apply    # rewrites them in place
```

The text is read out of the appendix of `LICENSE` rather than written down in the script, so the two
cannot disagree, and CI runs `check` on every pull request.
