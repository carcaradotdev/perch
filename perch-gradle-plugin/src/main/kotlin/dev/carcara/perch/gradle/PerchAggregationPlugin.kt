package dev.carcara.perch.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

private const val KOTLIN_MULTIPLATFORM_ID = "org.jetbrains.kotlin.multiplatform"

/** The usage the Kotlin Gradle plugin asks for when it resolves a `commonMain` dependency graph. */
private const val KOTLIN_METADATA_USAGE = "kotlin-metadata"

/**
 * Source-set dependency buckets the aggregator resolves through. These are siblings:
 * `commonMainImplementation` does not extend `commonMainApi`, so a producer depended on with
 * `api(project(...))` is invisible unless both are extended.
 */
private val GRAPH_CONFIGURATIONS = listOf("commonMainImplementation", "commonMainApi")

public abstract class PerchAggregationExtension {
  /** Package the generated `registerAllDeepLinks()` extension is emitted into. Required. */
  public abstract val outputPackage: Property<String>

  /** Fully qualified name of the parser the generated extension is declared on. */
  public abstract val parserClass: Property<String>
}

/**
 * Aggregator side of the Perch build pipeline. Collects the route manifest published by every
 * module reachable through this module's own dependencies and generates a single
 * `registerAllDeepLinks()` extension into `commonMain`.
 *
 * Discovery runs through the dependency graph rather than over `rootProject.subprojects`, so it
 * stays within the applying project and does not trip Isolated Projects.
 *
 * A dependency that publishes no manifest contributes no routes and does not fail the build.
 */
public class PerchAggregationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension =
      project.extensions.create("perchAggregation", PerchAggregationExtension::class.java)
    extension.parserClass.convention("dev.carcara.perch.DeepLinkParser")

    val outputDirectory = project.layout.buildDirectory.dir("generated/perch/commonMain/kotlin")
    val generate = project.tasks.register(
      "generateDeepLinkRegistration",
      GenerateDeepLinkRegistration::class.java,
    ) {
      group = "build"
      description = "Generates registerAllDeepLinks() from every reachable route manifest"
      outputPackage.set(extension.outputPackage)
      parserClass.set(extension.parserClass)
      this.outputDirectory.set(outputDirectory)
    }

    project.plugins.withId(KOTLIN_MULTIPLATFORM_ID) { configure(project, generate) }

    // afterEvaluate runs during configuration, before configuration-cache state is captured, so
    // holding `project` here is not a violation. Without Kotlin Multiplatform there is no
    // commonMain to generate into and no dependency graph to walk, and the task would otherwise
    // quietly generate an empty function - the exact silent failure this plugin has to avoid.
    project.afterEvaluate {
      if (!project.pluginManager.hasPlugin(KOTLIN_MULTIPLATFORM_ID)) {
        throw GradleException(
          "dev.carcara.perch.aggregation: Kotlin Multiplatform is not applied on ${project.path}. " +
            "Apply id(\"$KOTLIN_MULTIPLATFORM_ID\") in the same `plugins { }` block.",
        )
      }
    }
  }

  private fun configure(project: Project, generate: TaskProvider<GenerateDeepLinkRegistration>) {
    val objects = project.objects
    val manifestsConfiguration = project.configurations.resolvable("perchManifests") {
      description = "Route manifests published by this module's dependencies"
      GRAPH_CONFIGURATIONS.forEach { extendsFrom(project.configurations.getByName(it)) }
      // Walking the graph needs an unambiguous request: a Kotlin Multiplatform module publishes
      // eight or more variants, and an attribute-free resolution can only choose between them by
      // accident. Asking for what the Kotlin Gradle plugin itself asks for when it resolves
      // commonMain means this sees exactly the graph commonMain compiles against, on every target.
      attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, KOTLIN_METADATA_USAGE))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.common)
      }
    }

    // withVariantReselection() then picks each dependency's manifest variant rather than the
    // compile output selected above; lenient(true) is what lets a dependency publishing no
    // manifest at all - which is most of them - drop out instead of failing the build.
    val manifestArtifacts = manifestsConfiguration.get().incoming
      .artifactView {
        withVariantReselection()
        lenient(true)
        attributes { attribute(PERCH_MANIFEST_ATTRIBUTE, "true") }
      }
      .artifacts

    val manifestFiles =
      manifestArtifacts.artifactFiles.asFileTree.matching { include("perch-manifest-*.txt") }
    // Resolved at execution time, from a provider that holds the artifact collection rather than
    // the project. What `lenient(true)` swallows lands here, and the task reports it.
    val resolutionFailures = project.provider {
      manifestArtifacts.failures.map { failure ->
        generateSequence(failure as Throwable) { it.cause }
          .mapNotNull { it.message }
          .joinToString("\n    ")
      }
    }

    generate.configure {
      manifests.from(manifestFiles)
      this.resolutionFailures.set(resolutionFailures)
    }

    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.sourceSets.configureEach {
      // Handing the TaskProvider to srcDir makes every consumer of commonMain sources depend on
      // generation implicitly, so no consumer has to remember to wire the task up.
      if (name == "commonMain") kotlin.srcDir(generate)
    }
  }
}
