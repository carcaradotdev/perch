// A convention plugin: each module applies this to itself, rather than the root build reaching
// into subprojects (`subprojects { }`), which Isolated Projects forbids. See
// https://docs.gradle.org/current/userguide/isolated_projects.html

apply(plugin = "io.gitlab.arturbosch.detekt")

extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
  // `rootDir` is this project's own (immutable) view of the build's root directory - unlike
  // `rootProject.file(...)`, reading it is not a cross-project access.
  config.setFrom(rootDir.resolve("config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  parallel = true
}

// The detekt Gradle plugin creates one `Detekt` task per Kotlin source set/target on a
// multiplatform module (detektMetadataCommonMain, detektJvmMain, detektAndroidMain, ...), but the
// plain `detekt` lifecycle task it also registers depends on none of them, so it is NO-SOURCE for
// a Kotlin Multiplatform module and `check` inherits that blind spot. Wire `check` to the live
// collection of per-source-set tasks instead: `tasks.matching` and `tasks.withType` are both lazy
// and updated as tasks are registered, so this keeps working as later modules add their own
// source sets and targets without editing this file.
tasks.matching { it.name == "check" }.configureEach {
  dependsOn(tasks.withType<io.gitlab.arturbosch.detekt.Detekt>())
}
