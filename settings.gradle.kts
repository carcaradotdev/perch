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
include(":perch-test")
include(":perch-metro")
include(":perch-ksp")

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
  // The root project has no Kotlin source of its own; nothing else is exempt today; a future
  // module with a genuine reason to skip detekt should be added here explicitly; adding it here
  // is a visible decision, not a silent gap.
  if (path == ":") return@afterProject

  if (!pluginManager.hasPlugin("dev.carcara.perch.detekt")) {
    throw GradleException(
      "Project '$path' has no detekt coverage. Add `id(\"dev.carcara.perch.detekt\")` to the " +
        "`plugins { }` block in ${buildFile.relativeTo(rootDir)}."
    )
  }
}
