// A convention plugin: each published module applies this to itself, mirroring
// `dev.carcara.perch.detekt`, rather than the root build reaching into subprojects
// (`subprojects { }`), which Isolated Projects forbids. See
// https://docs.gradle.org/current/userguide/isolated_projects.html

apply(plugin = "com.vanniktech.maven.publish")
apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")

// The publishing coordinates belong to the plugin that publishes, not to a list somewhere else of
// which modules those are. `group` and `version` are only ever read for an artifact, so the module
// that gets them is exactly the module applying this line - a new published module is correct the
// moment it opts in, and nothing else in the build has to be told about it.
//
// The alternative, setting them for every project from `settings.gradle.kts`, is worse than
// redundant: two modules under one group whose leaf names match publish the same coordinate, and
// Gradle resolves that by silently substituting one project for the other. Feature modules named
// `api` and `impl` are the ordinary layout in the apps Perch is for, and `sample/` is laid out that
// way, so the build has to survive it. Left alone, a subproject's group defaults to its parent
// path, which is unique by construction.
group = "dev.carcara.perch"
version = "0.1.0-SNAPSHOT"

// Captured before entering `pom { }` below, where a bare `name` would resolve to the pom
// builder's own `name` property instead of this project's.
val moduleName = name

extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
  pom {
    name.set(moduleName)
    description.set("Type-safe deep links for Kotlin Multiplatform")
    url.set("https://github.com/carcaradotdev/perch")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
