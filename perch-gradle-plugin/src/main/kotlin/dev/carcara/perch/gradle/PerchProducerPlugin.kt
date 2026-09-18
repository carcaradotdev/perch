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

/**
 * Targets a module needs before Kotlin Multiplatform gives it a `commonMain` compilation, which is
 * the compilation the Perch processor runs on. Below this there is no `kspCommonMainKotlinMetadata`
 * task for the manifest artifact to be built by.
 */
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

    // The perch-manifest-*.txt files the processor writes land in the shared KSP resources
    // directory, so this publishes that directory and the consumer filters by filename.
    val kspManifestsDir = project.layout.buildDirectory.dir("generated/ksp/metadata/commonMain/resources")
    project.configurations.consumable("perchManifestElements") {
      attributes { attribute(PERCH_MANIFEST_ATTRIBUTE, "true") }
      outgoing.artifact(kspManifestsDir) { builtBy(KSP_METADATA_TASK) }
    }

    // `dependencies.addLater` defers this lambda to execution time, so it must not close over
    // `project` itself — that would pull a live `Project` reference into cached task state,
    // the single most common configuration-cache violation. Capture the `DependencyHandler`
    // value instead.
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
      // captures the project's path as a plain String rather than the `Project` itself.
      val projectPath = project.path
      val outputPackage = project.provider {
        extension.outputPackage.orNull
          ?: throw GradleException("dev.carcara.perch: set `perch.outputPackage` in $projectPath")
      }
      ksp.arg("perch.outputPackage", outputPackage)
    }

    project.plugins.withId(KOTLIN_MULTIPLATFORM_ID) { compileGeneratedRegistration(project) }

    // afterEvaluate runs during configuration, before configuration-cache state is captured, so
    // closing over `project` here (unlike the two deferred lambdas above) is not a CC violation.
    // It is also the earliest honest point for the target check below: targets are declared inside
    // the `kotlin { }` block, so nothing can count them until the build script has finished
    // running. Both checks throw during configuration, which is what puts the message in front of
    // the person before Gradle goes looking for a task that was never created.
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
   * Puts the processor's generated Kotlin on the module's own compile path, so the module can call
   * the `perchModuleParser()` it generates.
   *
   * Without this the module publishes a manifest for an aggregator and nothing else: the generated
   * file is written but no source set holds it and no compile task waits for it, which leaves the
   * single-module case - routes and a parser in one module, no aggregator anywhere - with a
   * function it cannot call.
   *
   * Unlike `dev.carcara.perch.aggregation`, which hands its generator's `TaskProvider` straight to
   * `srcDir` and lets every consumer inherit the dependency, this adds the directory by *path* and
   * wires the ordering separately. A `srcDir` carrying a `builtBy` is a directory the KSP metadata
   * task itself must wait for, since that task compiles `commonMain`, and Gradle rejects the
   * result outright: "Circular dependency between the following tasks:
   * kspCommonMainKotlinMetadata \--- kspCommonMainKotlinMetadata".
   *
   * So each consumer is named instead. The Kotlin compilations are the ones that matter; the
   * others read the directory without compiling it and fail validation rather than produce a wrong
   * answer ("uses this output of task ... without declaring an explicit or implicit dependency").
   * Detekt and the sources jars are matched by name because neither type is on this plugin's
   * classpath, and both are absent unless the module opts into them.
   *
   * Applying `dev.carcara.perch.aggregation` to the same module stays fine: that plugin generates
   * a differently named `perchParser()` into a directory of its own.
   */
  private fun compileGeneratedRegistration(project: Project) {
    val generatedSources =
      project.layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin")

    project.extensions.getByType(KotlinMultiplatformExtension::class.java)
      .sourceSets
      .configureEach { if (name == "commonMain") kotlin.srcDir(generatedSources) }

    project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
      if (name != KSP_METADATA_TASK) dependsOn(KSP_METADATA_TASK)
    }

    project.tasks
      .matching { it.name.startsWith("detekt") || it.name.endsWith("sourcesJar", ignoreCase = true) }
      .configureEach { dependsOn(KSP_METADATA_TASK) }
  }

  /**
   * Perch reads routes out of `commonMain`, and Kotlin Multiplatform only creates the `commonMain`
   * compilation - the one KSP registers `kspCommonMainKotlinMetadata` for, and the one this
   * plugin's manifest artifact is built by - for a module declaring two or more targets.
   *
   * Without this, a single-target module configures cleanly and fails much later with Gradle's own
   * `Task with name 'kspCommonMainKotlinMetadata' not found`, which names neither Perch, nor KSP,
   * nor the requirement, and which is raised in whichever module is doing the aggregating rather
   * than the one that is misconfigured.
   */
  private fun requireCommonMainCompilation(project: Project) {
    // Asking through `hasPlugin` rather than through `extensions.findByType` is what makes a named
    // message possible: `getByType` below throws
    // "Extension of type 'KotlinMultiplatformExtension' does not exist. Currently registered
    // extension types: [...]" - a dump of every extension in the project, naming neither Perch nor
    // the requirement. This is reached by a `kotlin("jvm")` module that applies KSP and Perch,
    // which is an ordinary shape, not a contrived one.
    //
    // It is not protection against the Kotlin Gradle plugin being absent from the classpath. That
    // failure is real (KGP is `compileOnly` here, so with no Kotlin plugin applied anywhere the
    // class genuinely does not load) but it is unreachable: getting this far needs KSP, KSP needs
    // a Kotlin plugin, and a Kotlin plugin is what puts the class on the classpath. A module with
    // no Kotlin plugin at all fails on the KSP check above, several lines earlier.
    if (!project.pluginManager.hasPlugin(KOTLIN_MULTIPLATFORM_ID)) {
      throw GradleException(
        "dev.carcara.perch: Kotlin Multiplatform is not applied on ${project.path}. Perch scans " +
          "commonMain, which only a multiplatform module has. Apply " +
          "id(\"$KOTLIN_MULTIPLATFORM_ID\") in the same `plugins { }` block.",
      )
    }

    // The metadata target is filtered out: it is the commonMain compilation itself, not one of the
    // targets whose existence creates it, so counting it would make every module look like it has
    // one more target than its build script declares.
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
