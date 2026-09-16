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
| `dev.carcara.perch:perch-core` | `@DeepLink`, `DeepLinkParser`, `DeepLinkLogger` |
| `dev.carcara.perch:perch-ksp` | The KSP processor; `dev.carcara.perch` adds it for you, so nothing in your build names it |

`perch-core` is Kotlin Multiplatform and depends on `kotlinx-serialization-core` and nothing else.
`perch-ksp` is a plain Kotlin/JVM module, not Multiplatform at all — KSP processors run on the JVM
regardless of the targets of the module they process, so it does not need to be. Both are version
`0.1.0-SNAPSHOT`.

## Quick start

This walks through the same thing `sample/` in this repository builds and tests end to end, in a
single-feature form. The sample itself is laid out the way an app is - a `sample-navigation` module
holding a route supertype, two features each owning the links they can be entered by, an aggregator,
and an installable app on each platform:

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

Two producers rather than one is the point of that shape: it is the only way the aggregation step
does something a single module could not do for itself.

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
`dev.carcara.perch`, becomes a registered route.** Nothing else registers it, and nothing outside
that module's own sources is scanned. If your app also uses Ktor's type-safe client, its `@Resource`
classes are HTTP resources and not deep links; Perch reads `@DeepLink` only, so the two never
collide.

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

This generates a `perchParser()` factory into `commonMain`, carrying every route reachable from
this module's own `commonMain` dependencies:

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
does happen — a hand-built `DeepLinkParser` nobody registered anything on — the first `parse` says
so through the `DeepLinkLogger` instead of quietly answering `null` forever.

### 3. Build a parser and resolve a link

```kotlin
import com.example.app.perchParser
import dev.carcara.perch.DeepLinkParser

fun appParser(): DeepLinkParser =
    perchParser(schemes = setOf("myapp", "https"), hosts = setOf("myapp.example"))
```

It is a plain function, so it drops into whatever DI you use — a Metro or Dagger `@Provides`, a
Koin `single { }` — without Perch knowing anything about it:

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

Seal that type and the `when` loses its `else` as well — but only if every route lives in the module
that declares the supertype, because Kotlin permits implementations of a sealed type nowhere else.
An app that splits routes across feature modules, which is the layout the sample uses, gets the
narrowing and not the exhaustiveness.

That is the whole surface: a route object, or `null`. Deciding when to act on it, how to navigate,
and whether the user is allowed to land there is your app's own logic, sitting on top of whatever
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
Perch demands no supertype the route classes are free to implement it — so `parse` returns an
object the back stack already accepts:

```kotlin
@DeepLink("/payments/{id}")
class PaymentLink(val id: String) : NavKey

val route = parser.parse(url)
if (route is NavKey) backStack.add(route)
```

`NavKey` asks that keys be serializable so `rememberNavBackStack` can restore them, which
`@DeepLink` has already arranged: it is `@MetaSerializable`, so the compiler generates the
serializer without a second annotation.

**Voyager** needs a second type. A `Screen` declares `@Composable fun Content()` — it *is* the UI,
so a shared route module implementing it would have to depend on Compose and carry the layout:

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

From there nothing downstream names a feature, or knows a generator was involved. `sample-di` holds
a router that injects that parser alongside a map of per-route handlers, and `features/payments/impl`
contributes one handler into that map — so a feature declares the links it owns and what happens
when one is opened, and neither needs a line in a central list. `sample-ios-app` implements the
router's navigation seam in Swift, which is the same graph reached from the other side.

Some navigators bring a deep-link feature of their own. It does not overlap with this one: Perch
decides what a URL means while it is still a URL, and hands over a typed object; what happens to
that object is the navigator's business.

## The codegen pipeline

Two Gradle plugins, applied to different modules:

- **`dev.carcara.perch`** — the *producer* plugin. Apply it to a module that declares routes. It
  runs the Perch KSP processor over that module's own `commonMain` sources, publishes the routes
  it finds on a `perchManifestElements` configuration for an aggregator to pick up, and generates
  an `internal fun perchModuleParser()` into that module's `commonMain`. A single-module app needs
  no aggregator at all: apply this plugin and call `perchModuleParser()`.
- **`dev.carcara.perch.aggregation`** — the *aggregator* plugin. Apply it to the module that
  assembles your app (or any module that wants a single `perchParser()` covering several
  producers). It walks this module's own `commonMain` dependency graph, collects every manifest it
  can reach, and generates the registration function.

A module can apply either, both, or neither — each of the sample's two feature modules applies only
the producer plugin and `sample-app` applies only the aggregator, which is the common shape.

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
error anywhere — so if a route is missing from `perchParser()`, check which source set its
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

No version has been released yet, so `mavenLocal()` is still how you consume Perch. Everything
around that is in place: three artifacts — `perch-core`, `perch-ksp` and `perch-gradle-plugin`,
the last alongside both plugin markers — publish under a binary-compatibility
(`apiCheck`/`apiDump`) guard, each carries the sources jar, javadoc jar and complete POM Maven
Central requires, and all three take their coordinates, licence, developer and SCM from one
convention plugin so a release cannot describe one of them differently from the others.

A release is a published GitHub Release whose tag is the version. The tag is the only place that
number lives: `.github/workflows/release.yml` passes it to both builds as
`ORG_GRADLE_PROJECT_version`, so there is no version bump commit and no way for the tag and the
artifacts to disagree. What it needs from the repository is four secrets —
`MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`, which are a Central Portal user token rather
than an account login, and `SIGNING_IN_MEMORY_KEY` with `SIGNING_IN_MEMORY_KEY_PASSWORD` for the
GPG key.

The workflow uploads and stops. Perch ships from two separate Gradle builds, so the Portal receives
two deployments and no single upload is the whole release; both sit staged at
[central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments)
until someone releases them together or drops both. Automatic release would mean a failure in the
second upload leaving the first already public and immutable.

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

CI runs all three for you on every pull request, in two jobs: `plugins` builds and tests
`build-logic` and `perch-gradle-plugin` on Linux, and `library` runs the root build on macOS,
which is the only host that can compile the Apple targets. Both finish by publishing to
`mavenLocal()`, so a break in the POM or in the sources and javadoc jars fails a pull request
rather than a release.

Running them yourself before opening one is still faster than waiting, and needs macOS for the
same reason.
