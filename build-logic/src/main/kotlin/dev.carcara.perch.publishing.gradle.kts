// A convention plugin: each published module applies this to itself, mirroring
// `dev.carcara.perch.detekt`, rather than the root build reaching into subprojects
// (`subprojects { }`), which Isolated Projects forbids. See
// https://docs.gradle.org/current/userguide/isolated_projects.html

apply(plugin = "com.vanniktech.maven.publish")

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
