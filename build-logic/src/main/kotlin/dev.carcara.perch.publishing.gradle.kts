// A convention plugin: each published module applies this to itself, mirroring
// `dev.carcara.perch.detekt`, rather than the root build reaching into subprojects
// (`subprojects { }`), which Isolated Projects forbids. See
// https://docs.gradle.org/current/userguide/isolated_projects.html
//
// Both Gradle builds that publish apply it - the root build's library modules and the separate
// `perch-gradle-plugin` build - which is why everything a POM says lives here rather than in a
// `gradle.properties`. The vanniktech plugin's own convention is `POM_*` properties read off the
// project, and with two builds that would mean two copies of the same eleven lines, free to drift
// into two different licences on two artifacts of one release.

// The `.base` plugin, not the main one. The main plugin reads those `POM_*` properties off the
// project itself, which is its documented Isolated Projects incompatibility; the base plugin
// configures nothing until asked, and everything below is that asking.
apply(plugin = "com.vanniktech.maven.publish.base")
apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")

// The publishing coordinates belong to the plugin that publishes, not to a list somewhere else of
// which modules those are. `group` is only ever read for an artifact, so the module that gets it is
// exactly the module applying this line - a new published module is correct the moment it opts in,
// and nothing else in the build has to be told about it.
//
// The alternative, setting it for every project from `settings.gradle.kts`, is worse than
// redundant: two modules under one group whose leaf names match publish the same coordinate, and
// Gradle resolves that by silently substituting one project for the other. Feature modules named
// `api` and `impl` are the ordinary layout in the apps Perch is for, and `sample/` is laid out that
// way, so the build has to survive it. Left alone, a subproject's group defaults to its parent
// path, which is unique by construction.
group = "dev.carcara.perch"

// The version a checkout builds at. Releasing does not edit it: the release workflow passes the
// number from the tag as `ORG_GRADLE_PROJECT_version`, and the line below prefers that when it is
// there. So the released number exists in one place that is also the announcement, there is no
// version-bump commit to forget, and a clone of main always builds a snapshot.
//
// Through `providers` rather than `project.version`, because a build that never sets the property
// leaves that at the string "unspecified" - which is not an error, just a coordinate nobody can
// resolve. Naming the fallback here means the two builds cannot disagree about it.
version = providers.gradleProperty("version").orNull?.takeIf(String::isNotBlank) ?: "0.1.0-SNAPSHOT"

// Captured before entering `pom { }` below, where a bare `name` would resolve to the pom
// builder's own `name` property instead of this project's.
val moduleName = name

extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
  // Which publications exist is decided by the plugins the module already applied: Kotlin
  // Multiplatform gets one per target plus the root module, a plain JVM module gets one, and
  // `java-gradle-plugin` gets the main publication plus a marker per plugin id - the marker being
  // what lets a consumer write `plugins { id("dev.carcara.perch") }`. This one line covers all
  // three shapes, which is what lets the Gradle plugin build share this file.
  configureBasedOnAppliedPlugins()

  // Uploads to the Central Portal and stops. Not `publishAndReleaseToMavenCentral`'s automatic
  // release: Perch ships from two separate Gradle builds, so the Portal receives two deployments
  // and no single upload can be the whole release. Released automatically, a failure in the second
  // build leaves the first already published and immutable - half a library on Maven Central.
  // Staged, both deployments sit there until a human sees three artifacts and releases them
  // together, or drops both.
  publishToMavenCentral()

  // Maven Central rejects an unsigned non-snapshot deployment. Snapshots are not signed and this
  // does not ask for a key for them, so `publishToMavenLocal` works in a checkout with no GPG
  // setup at all.
  signAllPublications()

  pom {
    name.set(moduleName)
    // A provider because a module sets its own `description` after this plugin is applied; read
    // eagerly it would always be the fallback.
    description.set(provider {
      project.description ?: "Type-safe deep links for Kotlin Multiplatform"
    })
    inceptionYear.set("2026")
    url.set("https://github.com/carcaradotdev/perch")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        distribution.set("repo")
      }
    }
    developers {
      developer {
        id.set("carcaradotdev")
        name.set("Carcara")
        url.set("https://github.com/carcaradotdev")
      }
    }
    scm {
      url.set("https://github.com/carcaradotdev/perch")
      connection.set("scm:git:git://github.com/carcaradotdev/perch.git")
      developerConnection.set("scm:git:ssh://git@github.com/carcaradotdev/perch.git")
    }
  }
}
