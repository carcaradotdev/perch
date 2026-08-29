package dev.carcara.perch.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Field indices in a `path|routeClassName|outputPackage|baseClass` manifest line. */
private const val ROUTE_CLASS_FIELD = 1
private const val BASE_CLASS_FIELD = 3

/** Generates `registerAllDeepLinks()` from every route manifest reachable from the applying module. */
@CacheableTask
public abstract class GenerateDeepLinkRegistration : DefaultTask() {

  /** Route manifests discovered through the applying module's own dependency graph. */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val manifests: ConfigurableFileCollection

  /** Package the generated extension is emitted into. Required, and deliberately without a convention. */
  @get:Input
  public abstract val outputPackage: Property<String>

  /**
   * Fully qualified name of the app's route type, used as the parser's type argument.
   *
   * Optional: it normally comes from the manifests, since the processor writes into each one the
   * type it inferred for that module. Set it when the reachable producers disagree, which is what
   * happens when each feature module has a route type of its own under a shared one.
   */
  @get:Input
  @get:Optional
  public abstract val targetBaseClass: Property<String>

  /** Fully qualified name of the parser the generated extension is declared on. */
  @get:Input
  public abstract val parserClass: Property<String>

  /** Directory added to `commonMain`, holding the single generated file. */
  @get:OutputDirectory
  public abstract val outputDirectory: DirectoryProperty

  /**
   * What lenient resolution swallowed while looking for manifests. Reported, never an input: a
   * dependency that fails to resolve must not change the generated file, only explain itself.
   */
  @get:Internal
  public abstract val resolutionFailures: ListProperty<String>

  @TaskAction
  public fun generate() {
    val packageName = outputPackage.get()
    val parserFqn = parserClass.get()

    val manifestFiles = manifests.files.sortedBy { it.invariantSeparatorsPath }
    val routes = mutableListOf<String>()
    val baseClasses = mutableSetOf<String>()
    val unparseable = mutableListOf<String>()
    manifestFiles.forEach { file -> read(file, routes, baseClasses, unparseable) }

    val registered = routes.distinct().sorted()
    write(packageName, parserFqn, baseClassOf(baseClasses), registered)
    report(manifestFiles, registered, unparseable, packageName)
  }

  private fun read(
    file: File,
    routes: MutableList<String>,
    baseClasses: MutableSet<String>,
    unparseable: MutableList<String>,
  ) {
    file.readLines().forEachIndexed { index, line ->
      if (line.isBlank()) return@forEachIndexed
      val fields = line.split('|')
      val routeClass = fields.getOrNull(ROUTE_CLASS_FIELD).orEmpty().trim()
      if (routeClass.isEmpty()) {
        unparseable += "${file.name}:${index + 1}: $line"
      } else {
        routes += routeClass
        fields.getOrNull(BASE_CLASS_FIELD)?.trim()?.takeIf { it.isNotEmpty() }
          ?.let { baseClasses += it }
      }
    }
  }

  /**
   * The type the generated extension is declared on: the one every reachable producer inferred, or
   * whatever [targetBaseClass] names. Null when there is nothing to register, which is the one case
   * where no type is needed.
   */
  private fun baseClassOf(baseClasses: Set<String>): String? {
    targetBaseClass.orNull?.let { return it }
    if (baseClasses.size <= 1) return baseClasses.firstOrNull()

    throw GradleException(
      "Perch: the reachable modules declare their routes under ${baseClasses.size} different " +
        "route types (${baseClasses.sorted().joinToString()}), so registerAllDeepLinks() cannot " +
        "be declared on one of them. Name the type they all share with " +
        "perchAggregation { targetBaseClass.set(\"com.acme.Route\") }.",
    )
  }

  private fun write(packageName: String, parserFqn: String, baseFqn: String?, routes: List<String>) {
    // Only the parser is imported. A route is written fully qualified on purpose: importing route
    // types by simple name collides the moment two modules declare `com.acme.a.Details` and
    // `com.acme.b.Details`, and an aggregator that spans every module in an app makes that
    // ordinary rather than rare.
    val registrations = routes.joinToString("\n") { "  register<$it>()" }
    // With no routes there is no type to declare the extension on, and none is needed: a function
    // generic in the parser's type argument compiles against whatever parser the app has.
    val signature = if (baseFqn == null) {
      "public fun <T : Any> ${parserFqn.substringAfterLast('.')}<T>.registerAllDeepLinks()"
    } else {
      "public fun ${parserFqn.substringAfterLast('.')}<$baseFqn>.registerAllDeepLinks()"
    }
    // Not `DeepLinkRegistration.kt`: that is what the KSP processor writes into a producer's own
    // outputPackage, and a module that both declares a route and aggregates - an app module with
    // one route in it - would otherwise get two files of that name in one package and a
    // duplicate-JVM-facade error naming neither Perch nor the reason.
    val outputFile = outputDirectory.get()
      .file(packageName.replace('.', '/') + "/PerchDeepLinkRegistration.kt")
      .asFile
    outputFile.parentFile.mkdirs()
    outputFile.writeText(
      """
      |package $packageName
      |
      |import $parserFqn
      |
      |/**
      | * Registers every deep-link route reachable from this module.
      | * @generated by the generateDeepLinkRegistration Gradle task
      | */
      |$signature {
      |$registrations
      |}
      |
      """.trimMargin(),
    )
  }

  /**
   * Manifests resolve leniently, so a dependency whose manifest failed to resolve and a dependency
   * that publishes no manifest at all produce the same empty result. Without this, the only symptom
   * of the first case is a deep link that never resolves at runtime, months later.
   */
  private fun report(
    manifestFiles: List<File>,
    routes: List<String>,
    unparseable: List<String>,
    packageName: String,
  ) {
    if (unparseable.isNotEmpty()) {
      logger.warn(
        "Perch: ignored ${unparseable.size} manifest line(s) that carry no route class in field " +
          "${ROUTE_CLASS_FIELD + 1} of `path|routeClassName|outputPackage`. Any route they meant " +
          "to declare is missing from $packageName.registerAllDeepLinks():\n" +
          unparseable.joinToString("\n") { "  - $it" },
      )
    }

    val failures = resolutionFailures.getOrElse(emptyList())
    if (failures.isNotEmpty()) {
      logger.warn(
        "Perch: could not resolve ${failures.size} of this module's dependencies while looking " +
          "for route manifests. Any route they declare is missing from " +
          "$packageName.registerAllDeepLinks():\n" +
          failures.joinToString("\n") { "  - $it" },
      )
    }

    val found = manifestFiles.joinToString("\n") { "  $it" }
    if (routes.isEmpty()) {
      logger.warn(
        "Perch: no deep-link routes were discovered for $packageName, so the generated " +
          "registerAllDeepLinks() registers nothing. Manifests are resolved leniently: a " +
          "dependency whose manifest failed to resolve is indistinguishable here from one that " +
          "publishes no manifest. Manifests found: ${manifestFiles.size}." +
          if (manifestFiles.isEmpty()) "" else "\n$found",
      )
    } else {
      logger.lifecycle(
        "Perch: generated registerAllDeepLinks() for $packageName with ${routes.size} route(s) " +
          "from ${manifestFiles.size} manifest(s):\n$found",
      )
    }
  }
}
