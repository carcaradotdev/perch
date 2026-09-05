import org.gradle.api.GradleException

pluginManagement {
  includeBuild("build-logic")
  includeBuild("perch-gradle-plugin")
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "perch"

include(":perch-core")
include(":perch-ksp")

// The sample is not published. It is laid out the way an app that uses Perch is laid out: each
// feature owns the links it can be entered by, in its own `api` module applying the producer
// plugin, and one module aggregates them. With two producers the aggregation step is doing
// something a single-producer sample could not show.
//
// `sample-android` is an installable app that takes the route objects that pipeline produces and
// hands them to two multiplatform navigation libraries. Naming these paths also creates the
// intermediate projects `:sample` and `:sample:features`, which have no build file and no source
// of their own.
include(":sample:sample-navigation")
include(":sample:features:home:api")
include(":sample:features:payments:api")
include(":sample:sample-app")
include(":sample:sample-android")

// Isolated Projects forbids `subprojects { }` (it is cross-project access from root-project
// scope). `beforeProject` registered here runs once per project in an isolated context private
// to that project, which is the sanctioned replacement: see
// https://docs.gradle.org/current/userguide/isolated_projects.html
//
// Detekt's own application, its extension configuration and the `check` -> `Detekt` task wiring
// live in the `dev.carcara.perch.detekt` convention plugin (see `build-logic/`) instead of here:
// adding the detekt Gradle plugin to this settings script's own classpath, so this block could
// reference `DetektExtension` directly, puts it on the classloader every project script shares -
// which conflicts with the Kotlin Multiplatform plugin. A convention plugin applied per-project
// keeps that classpath isolated to the projects that opt into it.
gradle.lifecycle.beforeProject {
  // Only the modules that publish. `group` and `version` are publishing coordinates, and pinning
  // them on everything is what breaks a build whose leaf module names repeat: the sample's two
  // feature modules are both called `api`, so a uniform group makes both `dev.carcara.perch:api`
  // and Gradle resolves the collision by substituting one for the other -
  //
  //     project ':sample:features:payments:api' -> project ':sample:features:home:api'
  //
  // which silently drops a whole feature's deep links from the aggregated parser. Left alone, a
  // subproject's group defaults to its parent path, which is unique by construction. Feature
  // modules named `api` and `impl` are the common layout in the apps Perch is for, so the sample
  // is laid out that way and this rule has to survive it.
  if (path !in setOf(":perch-core", ":perch-ksp")) return@beforeProject

  group = "dev.carcara.perch"
  version = "0.1.0-SNAPSHOT"
}

// Detekt coverage is opt-in per module now (`id("dev.carcara.perch.detekt")` in that module's own
// `plugins { }` block) rather than automatic the way `subprojects { }` used to make it. A module
// that forgets the line gets no error and `check` still goes green while nothing lints it - the
// same gate-reports-success-while-checking-nothing failure this task exists to close, just moved
// up from per-source-set blindness to per-module blindness. Fail loudly instead: once a project
// has finished evaluating (so its own `plugins { }` block has already run), confirm it applied the
// convention plugin. Reading `pluginManager` here is this project inspecting its own state from
// within its own isolated `afterProject` context, not reaching into a neighbour.
gradle.lifecycle.afterProject {
  // What is exempt is decided by what a project compiles, not by what it applies or what it is
  // called: a project with no Kotlin compilation gives detekt nothing to lint, so requiring the
  // convention plugin there would only be noise. That covers the root project, and it covers the
  // container project Gradle creates implicitly for a nested path - `include(":sample:sample-app")`
  // brings `:sample` into the build with no build file and no source.
  //
  // Asking for the `kotlin` extension rather than for plugin ids is what keeps this honest. A list
  // of Kotlin Gradle Plugin ids looks structural and is not: AGP 9 compiles Kotlin itself, so
  // `sample-android` applies `com.android.application` and no `org.jetbrains.kotlin.*` plugin at
  // all, and a check written against those ids waves it through - silently exempting the newest
  // module in the build, which is the one most likely to be copied. Whoever sets up the Kotlin
  // compilation registers the extension, so this holds for the multiplatform, jvm and android
  // plugins alike, for AGP's built-in Kotlin, and for whatever supersedes them.
  //
  // The compile tasks would be a more direct signal and are not available yet: AGP registers its
  // per-variant tasks after this callback runs, so at this point `sample-android` has no
  // `compileDebugKotlin` to find.
  if (extensions.findByName("kotlin") == null) return@afterProject

  if (!pluginManager.hasPlugin("dev.carcara.perch.detekt")) {
    throw GradleException(
      "Project '$path' has no detekt coverage. Add `id(\"dev.carcara.perch.detekt\")` to the " +
        "`plugins { }` block in ${buildFile.relativeTo(rootDir)}."
    )
  }
}
