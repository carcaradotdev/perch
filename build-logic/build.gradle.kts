plugins {
  `kotlin-dsl`
}

kotlin { jvmToolchain(21) }

dependencies {
  // A precompiled script plugin that applies detekt and configures `DetektExtension` needs
  // detekt's own Gradle plugin classes on this build's classpath, both to resolve `apply(plugin
  // = "io.gitlab.arturbosch.detekt")` and to compile the type references in that script.
  implementation(libs.detekt.gradlePlugin)
  // Same reasoning for the publishing convention plugin: it references
  // `MavenPublishBaseExtension` and applies both plugins by id, so both need to be on this
  // build's own classpath.
  implementation(libs.mavenPublish.gradlePlugin)
  implementation(libs.binaryCompatibilityValidator.gradlePlugin)
}
