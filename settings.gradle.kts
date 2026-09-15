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
include(":sample:sample-ios")

// Detekt is opt-in per module: `id("dev.carcara.perch.detekt")` in that module's own `plugins { }`
// block. Isolated Projects forbids `subprojects { }` - it is cross-project access from root-project
// scope - so a convention plugin each module applies to itself is the sanctioned shape here, and
// the one the publishing coordinates use too. See
// https://docs.gradle.org/current/userguide/isolated_projects.html
//
// The detekt Gradle plugin also stays off this script's classpath deliberately, which is why its
// extension and its `check` -> `Detekt` task wiring live in the convention plugin rather than in a
// block below. Adding it here so a block could reference `DetektExtension` directly would put it on
// the classloader every project script shares, which conflicts with the Kotlin Multiplatform
// plugin.
//
// What opting in costs is that a module which forgets the line gets no error, and `check` goes
// green while nothing lints it - the same gate-reports-success-while-checking-nothing failure the
// task exists to close, moved up from per-source-set blindness to per-module blindness. So fail
// loudly: once a project has finished evaluating, and its own `plugins { }` block has therefore
// run, confirm it applied the convention plugin. Reading `pluginManager` here is this project
// inspecting its own state from within its own isolated `afterProject` context, not reaching into
// a neighbour.
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
