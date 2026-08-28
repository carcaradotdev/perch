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
