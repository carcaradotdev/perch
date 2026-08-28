package dev.carcara.perch.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

private const val KOTLIN_MULTIPLATFORM_ID = "org.jetbrains.kotlin.multiplatform"

/**
 * Targets a module needs before Kotlin Multiplatform gives it a `commonMain` compilation, which is
 * the compilation the Perch processor runs on. Below this there is no `kspCommonMainKotlinMetadata`
 * task for the manifest artifact to be built by.
 */
private const val MINIMUM_TARGETS = 2

public abstract class PerchExtension {
  /**
   * Package the generated `registerDeepLinks()` extension is emitted into. Required.
   * A blank value skips codegen for the module while its manifest directory still publishes.
   */
  public abstract val outputPackage: Property<String>

  /**
   * Coordinates of the KSP processor: a Maven coordinate (`"group:artifact"`) or, for a build
   * that carries the processor as an included project, a project path (`":perch-ksp"`).
   */
  public abstract val processorCoordinates: Property<String>

  /** Fully qualified name of the interface a route must implement to count as a deep link. */
  public abstract val targetBaseClass: Property<String>

  /** Fully qualified name of the parser the generated extension targets. */
  public abstract val parserClass: Property<String>
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
    extension.processorCoordinates.convention("dev.carcara.perch:perch-ksp")
    extension.targetBaseClass.convention("dev.carcara.perch.DeepLinkTarget")
    extension.parserClass.convention("dev.carcara.perch.DeepLinkParser")

    // The perch-manifest-*.txt files the processor writes land in the shared KSP resources
    // directory, so this publishes that directory and the consumer filters by filename.
    val kspManifestsDir = project.layout.buildDirectory.dir("generated/ksp/metadata/commonMain/resources")
    project.configurations.consumable("perchManifestElements") {
      attributes { attribute(PERCH_MANIFEST_ATTRIBUTE, "true") }
      outgoing.artifact(kspManifestsDir) { builtBy("kspCommonMainKotlinMetadata") }
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
      ksp.arg("perch.targetBaseClass", extension.targetBaseClass)
      ksp.arg("perch.parserClass", extension.parserClass)
    }

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
    // The plugin id is checked as a string, and `KotlinMultiplatformExtension` is only touched
    // afterwards. The Kotlin Gradle plugin is `compileOnly` here, so on a build where it was never
    // applied that class is not loadable at all and naming it first would throw
    // NoClassDefFoundError instead of the message below.
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
