/*
 * Copyright 2026 Carcara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.carcara.perch.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

/** Targets a module needs before Kotlin Multiplatform gives it a `commonMain` compilation. */
private const val MINIMUM_TARGETS = 2

public abstract class PerchExtension {
  /**
   * Package the generated `perchModuleParser()` factory is emitted into. Required.
   * A blank value skips codegen for the module while its manifest directory still publishes.
   */
  public abstract val outputPackage: Property<String>

  /**
   * Coordinates of the KSP processor: a Maven coordinate (`"group:artifact:version"`) or, for a
   * build that carries the processor as an included project, a project path (`":perch-ksp"`).
   *
   * Defaults to the `perch-ksp` artifact published from the same version as this plugin, so a
   * consumer only sets this to point at a processor somewhere else.
   */
  public abstract val processorCoordinates: Property<String>
}

/**
 * Producer side of the Perch build pipeline. Runs the Perch KSP processor over the module's
 * commonMain sources and publishes the resulting route manifests on a consumable
 * `perchManifestElements` configuration for `dev.carcara.perch.aggregation` to collect.
 *
 * Apply this to the module that *declares* its routes. The processor scans that module's own
 * sources, so a route declared elsewhere belongs to whichever module declares it.
 */
public class PerchProducerPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("perch", PerchExtension::class.java)
    extension.processorCoordinates.convention("dev.carcara.perch:perch-ksp:${PerchVersion.value}")

    // The processor writes its manifests into the shared KSP resources directory, so this
    // publishes the whole directory and the consumer filters by filename.
    val kspManifestsDir = project.layout.buildDirectory.dir("generated/ksp/metadata/commonMain/resources")
    project.configurations.consumable("perchManifestElements") {
      attributes { attribute(PERCH_MANIFEST_ATTRIBUTE, "true") }
      outgoing.artifact(kspManifestsDir) { builtBy(KSP_METADATA_TASK) }
    }

    // `dependencies.addLater` defers this lambda to execution time, so it must not close over
    // `project` itself: that pulls a live `Project` into cached task state. Capture the handler.
    val dependencyHandler = project.dependencies
    project.configurations.matching { it.name == "kspCommonMainMetadata" }.configureEach {
      dependencies.addLater(
        extension.processorCoordinates.map { coordinate ->
          if (coordinate.startsWith(":")) {
            dependencyHandler.project(mapOf("path" to coordinate))
          } else {
            dependencyHandler.create(coordinate)
          }
        },
      )
    }

    project.plugins.withId("com.google.devtools.ksp") {
      val ksp = project.extensions.getByType(KspExtension::class.java)
      // Same reason as above: `ksp.arg`'s Provider overload defers this lambda too, so it
      // captures the path as a String rather than the `Project`.
      val projectPath = project.path
      val outputPackage = project.provider {
        extension.outputPackage.orNull
          ?: throw GradleException("dev.carcara.perch: set `perch.outputPackage` in $projectPath")
      }
      ksp.arg("perch.outputPackage", outputPackage)
      // Manifests are named after the declaring module, so two modules sharing an output package
      // still publish two distinguishable files.
      ksp.arg("perch.moduleId", projectPath)
    }

    project.plugins.withId(KOTLIN_MULTIPLATFORM_ID) { compileGeneratedRegistration(project) }

    // afterEvaluate runs during configuration, before configuration-cache state is captured, so
    // closing over `project` here is not a violation. It is also the earliest point that can count
    // targets, since they are declared inside the `kotlin { }` block.
    project.afterEvaluate {
      if (!project.pluginManager.hasPlugin("com.google.devtools.ksp")) {
        throw GradleException(
          "dev.carcara.perch: KSP is not configured on ${project.path}. Apply " +
            "id(\"com.google.devtools.ksp\") before dev.carcara.perch.",
        )
      }
      requireCommonMainCompilation(project)
    }
  }

  /**
   * Puts the processor's generated Kotlin on the module's own compile path, so a module with no
   * aggregator anywhere can still call the `perchModuleParser()` it generates.
   *
   * The directory is added by path and the ordering wired separately, task by task. A `srcDir`
   * carrying a `builtBy` would be a directory the KSP metadata task must wait for, and that task
   * is the one compiling `commonMain`: "Circular dependency between the following tasks:
   * kspCommonMainKotlinMetadata \--- kspCommonMainKotlinMetadata". Detekt and the sources jars are
   * matched by name because neither type is on this plugin's classpath.
   */
  private fun compileGeneratedRegistration(project: Project) {
    val generatedSources =
      project.layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin")

    project.extensions.getByType(KotlinMultiplatformExtension::class.java)
      .sourceSets
      .named("commonMain") { kotlin.srcDir(generatedSources) }

    project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
      if (name != KSP_METADATA_TASK) dependsOn(KSP_METADATA_TASK)
    }

    project.tasks
      .matching { it.name.startsWith("detekt") || it.name.endsWith("sourcesJar", ignoreCase = true) }
      .configureEach { dependsOn(KSP_METADATA_TASK) }
  }

  /**
   * Perch reads routes out of `commonMain`, which Kotlin Multiplatform only creates for a module
   * declaring two or more targets. Without this check a single-target module configures cleanly
   * and fails later with Gradle's own `Task with name 'kspCommonMainKotlinMetadata' not found`,
   * raised in whichever module aggregates rather than in the one that is misconfigured.
   */
  private fun requireCommonMainCompilation(project: Project) {
    // Asked through `hasPlugin` rather than `findByType` so a `kotlin("jvm")` module gets this
    // message: `getByType` below would instead dump every extension registered in the project.
    if (!project.pluginManager.hasPlugin(KOTLIN_MULTIPLATFORM_ID)) {
      throw GradleException(
        "dev.carcara.perch: Kotlin Multiplatform is not applied on ${project.path}. Perch scans " +
          "commonMain, which only a multiplatform module has. Apply " +
          "id(\"$KOTLIN_MULTIPLATFORM_ID\") in the same `plugins { }` block.",
      )
    }

    // The metadata target is the commonMain compilation itself, not one of the targets whose
    // existence creates it, so counting it would inflate every module by one.
    val targets = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
      .targets
      .filter { it.platformType != KotlinPlatformType.common }
      .map { it.name }
      .sorted()
    if (targets.size >= MINIMUM_TARGETS) return

    throw GradleException(
      "dev.carcara.perch: ${project.path} declares ${targets.size} Kotlin " +
        "${if (targets.size == 1) "target" else "targets"} " +
        "(${targets.joinToString().ifEmpty { "none" }}), and Perch needs at least two. Kotlin " +
        "Multiplatform only gives a module a shared commonMain compilation once it has two or " +
        "more targets, and that compilation is the one Perch's processor runs on - with a single " +
        "target there is nothing for it to scan. Declare the other targets this module is built " +
        "for, or move its routes into a module that has them.",
    )
  }
}
