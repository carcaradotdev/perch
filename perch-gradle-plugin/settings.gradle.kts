// The convention plugin that publishes lives in `build-logic`, and this is a separate Gradle
// build from the root one, so it has to include it for itself. Nothing else here comes from
// there: the two Gradle plugins this build compiles are what the root build's `sample/` applies,
// which is the whole reason this is a separate build in the first place.
pluginManagement {
  includeBuild("../build-logic")
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
    gradlePluginPortal()
  }
  versionCatalogs {
    create("libs") { from(files("../gradle/libs.versions.toml")) }
  }
}

rootProject.name = "perch-gradle-plugin"
