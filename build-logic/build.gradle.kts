plugins {
  `kotlin-dsl`
}

kotlin { jvmToolchain(21) }

dependencies {
  // A precompiled script plugin that applies detekt and configures `DetektExtension` needs
  // detekt's own Gradle plugin classes on this build's classpath, both to resolve `apply(plugin
  // = "io.gitlab.arturbosch.detekt")` and to compile the type references in that script.
  implementation(libs.detekt.gradlePlugin)
}
