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
  // What is exempt is decided structurally, not by name: a project that applies no Kotlin plugin
  // compiles nothing, so detekt would have no source to lint there and requiring the convention
  // plugin would only be noise. That covers the root project, and it covers the container project
  // Gradle creates implicitly for a nested path - `include(":sample:sample-app")` brings `:sample`
  // into the build with no build file and no source. A hardcoded path list would have to grow an
  // entry every time either of those appears, and an entry added to silence an error is exactly
  // how a real module ends up exempt by accident.
  val kotlinPluginIds = listOf(
    "org.jetbrains.kotlin.multiplatform",
    "org.jetbrains.kotlin.jvm",
    "org.jetbrains.kotlin.android",
  )
  if (kotlinPluginIds.none { pluginManager.hasPlugin(it) }) return@afterProject

  if (!pluginManager.hasPlugin("dev.carcara.perch.detekt")) {
    throw GradleException(
      "Project '$path' has no detekt coverage. Add `id(\"dev.carcara.perch.detekt\")` to the " +
        "`plugins { }` block in ${buildFile.relativeTo(rootDir)}."
    )
  }
}
