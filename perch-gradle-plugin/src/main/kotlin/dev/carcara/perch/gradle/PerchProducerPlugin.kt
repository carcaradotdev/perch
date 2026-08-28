package dev.carcara.perch.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

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
    project.afterEvaluate {
      if (!project.pluginManager.hasPlugin("com.google.devtools.ksp")) {
        throw GradleException(
          "dev.carcara.perch: KSP is not configured on ${project.path}. Apply " +
            "id(\"com.google.devtools.ksp\") before dev.carcara.perch.",
        )
      }
    }
  }
}
