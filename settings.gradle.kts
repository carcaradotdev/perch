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

// The sample is not published. `sample-routes` and `sample-app` run the whole KSP-and-aggregation
// pipeline; `sample-android` is an installable app that takes the route objects that pipeline
// produces and hands them to three different navigation libraries. Naming these paths also creates
// the intermediate project `:sample`, which has no build file and no source of its own.
include(":sample:sample-routes")
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
  // The root project itself is excluded, matching what `subprojects { }` used to cover.
  if (path == ":") return@beforeProject

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
