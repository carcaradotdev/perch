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

package dev.carcara.perch.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import dev.carcara.perch.joinDeepLinkPattern
import dev.carcara.perch.manifest.MANIFEST_FILE_EXTENSION
import dev.carcara.perch.manifest.ManifestRoute
import dev.carcara.perch.manifest.manifestFileNameFor
import dev.carcara.perch.manifest.renderManifestLine
import dev.carcara.perch.patternsConflict
import java.io.OutputStreamWriter

/**
 * KSP processor that generates a `perchModuleParser()` factory over the module's routes.
 *
 * The rule is one sentence: a class in this module's own sources annotated
 * `@dev.carcara.perch.DeepLink` is a route. There is no supertype to implement and nothing else to
 * satisfy, and the processor never looks outside the module it runs on.
 */
internal class DeepLinkProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val options: Map<String, String>,
) : SymbolProcessor {

  private companion object {
    private const val DEEP_LINK_ANNOTATION = "dev.carcara.perch.DeepLink"
    private val DEEP_LINK_SHORT_NAME = DEEP_LINK_ANNOTATION.substringAfterLast('.')
    private const val PARSER_CLASS = "dev.carcara.perch.DeepLinkParser"
    private const val LOGGER_CLASS = "dev.carcara.perch.DeepLinkLogger"
  }

  private var processed = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (processed) return emptyList()
    processed = true

    val outputPackage = options["perch.outputPackage"].orEmpty()
    // The declaring module's Gradle path, which the producer plugin passes and manifests are named
    // after. A processor run by hand has no plugin to pass it, so the output package stands in.
    val moduleId = options["perch.moduleId"]?.takeIf { it.isNotBlank() }
      ?: outputPackage.ifBlank { "root" }

    val routeClasses = mutableSetOf<KSClassDeclaration>()
    resolver.getAllFiles().forEach { file ->
      file.declarations.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
        collectRoutes(classDecl, routeClasses)
      }
    }

    if (routeClasses.isEmpty()) return emptyList()

    val routes = manifestRoutes(moduleId, routeClasses.toList()) ?: return emptyList()
    if (routes.isEmpty()) return emptyList()

    val routeSources = routeClasses.mapNotNull { it.containingFile }.distinct().toTypedArray()
    generateManifestFile(moduleId, routes, routeSources)

    // A blank output package is how a module says it contributes its routes to an aggregator
    // without wanting a parser of its own. Its manifest is written above either way, which is what
    // `PerchExtension.outputPackage` promises and what makes the setting about codegen alone.
    if (outputPackage.isNotBlank()) {
      generateRegistrationFile(outputPackage, routes, routeSources)
    }

    return emptyList()
  }

  private fun collectRoutes(
    classDecl: KSClassDeclaration,
    result: MutableSet<KSClassDeclaration>,
  ) {
    if (deepLinkAnnotation(classDecl) != null) {
      result.add(classDecl)
    }

    classDecl.declarations
      .filterIsInstance<KSClassDeclaration>()
      .forEach { nested ->
        collectRoutes(nested, result)
      }
  }

  /**
   * The `@DeepLink` on [classDecl], or null when it carries none.
   *
   * Matching the short name first is what keeps this cheap. Resolving an annotation's type is a
   * KSP type-resolution, and every class in the module arrives here with its full annotation list -
   * `@Serializable`, `@Inject`, `@Composable` and the rest - so resolving each one only to reject
   * it is the most-repeated work in the processor. The short name settles almost all of them.
   */
  private fun deepLinkAnnotation(classDecl: KSClassDeclaration): KSAnnotation? =
    classDecl.annotations.find { annotation ->
      annotation.shortName.asString() == DEEP_LINK_SHORT_NAME &&
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == DEEP_LINK_ANNOTATION
    }

  /**
   * Both generated files declare the route sources they were built from, and declare themselves
   * aggregating, because that is what they are: the processor scans the whole module.
   *
   * `Dependencies(aggregating = false)` with no source files, which is what this used to pass,
   * says the output depends on nothing. The generated file lands in `commonMain`, so the next
   * build sees it as a changed source and KSP runs an incremental round; an output that depends on
   * nothing is not attributed to any surviving source, so KSP deletes it. The build after that
   * finds the file missing, regenerates it, and the two states alternate forever - green, red,
   * green, red. Repeated `./gradlew build` runs over `sample/` are what catches this; nothing in
   * the compile-testing suite reaches KSP's incremental machinery.
   */
  /**
   * One [ManifestRoute] per route in [routeClasses], or null once an error has been logged.
   *
   * Reporting through the logger, rather than throwing, is what fails the KSP round with a
   * COMPILATION_ERROR instead of an uncaught-exception INTERNAL_ERROR: KSP fails the build once any
   * error is logged, without needing the round to unwind via an exception.
   */
  private fun manifestRoutes(
    moduleId: String,
    routeClasses: List<KSClassDeclaration>,
  ): List<ManifestRoute>? {
    val routes = mutableListOf<ManifestRoute>()

    routeClasses.forEach { classDecl ->
      val pattern = composedPattern(classDecl) ?: return@forEach
      val routeName = classDecl.qualifiedName?.asString() ?: return@forEach

      val clash = routes.firstOrNull { patternsConflict(pattern, it.pattern) }
      if (clash != null) {
        logger.error(
          "Deep link collision detected. Pattern '$pattern' conflicts with '${clash.pattern}', " +
            "already registered by '${clash.routeClassName}', so '$routeName' cannot be registered.",
          classDecl,
        )
        return null
      }
      routes += ManifestRoute(pattern = pattern, routeClassName = routeName, moduleId = moduleId)
    }

    return routes
  }

  /**
   * The full path pattern of [classDecl], its parents' patterns included.
   *
   * A nested route's pattern is its own `@DeepLink` path appended to its parent's: the parser
   * derives it that way from the serial descriptor, and a processor that read only the annotation
   * would check a pattern the parser never registers. Two sibling routes each annotated `/{id}`
   * under different parents would then fail the build for a collision that does not exist, and a
   * nested `/orders/{id}` that genuinely does collide with a top-level one would pass.
   *
   * The parent is the property whose own type is a route, and there is at most one: a route
   * reached by two different paths would have two patterns and no way to choose between them.
   */
  private fun composedPattern(classDecl: KSClassDeclaration): String? {
    val segments = mutableListOf<String>()
    val visited = mutableSetOf<String>()

    var current: KSClassDeclaration? = classDecl
    while (current != null) {
      val declaration = current
      val name = declaration.qualifiedName?.asString() ?: return null
      if (!visited.add(name)) {
        logger.error("Deep link route '$name' is reachable from itself through its parents.", classDecl)
        return null
      }
      segments += deepLinkPath(declaration) ?: return null

      val parents = declaration.getAllProperties().filter(::isRoute).toList()
      if (parents.size > 1) {
        logger.error("There are multiple parents for deep link '$name'.", classDecl)
        return null
      }
      current = parents.firstOrNull()?.type?.resolve()?.declaration as? KSClassDeclaration
    }

    return joinDeepLinkPattern(segments)
  }

  private fun isRoute(property: KSPropertyDeclaration): Boolean {
    val declaration = property.type.resolve().declaration as? KSClassDeclaration ?: return false
    return deepLinkAnnotation(declaration) != null
  }

  private fun generateRegistrationFile(
    packageName: String,
    routes: List<ManifestRoute>,
    routeSources: Array<KSFile>,
  ) {
    val file = codeGenerator.createNewFile(
      dependencies = Dependencies(aggregating = true, *routeSources),
      packageName = packageName,
      fileName = "DeepLinkRegistration",
    )

    OutputStreamWriter(file).use { writer ->
      // Only the parser and the logger are imported: they are the ones named in the signature.
      // Route types are written fully qualified, on purpose. Importing them by simple name would
      // collide the moment two routes in this module share a simple name from different packages
      // - `com.acme.a.Details` and `com.acme.b.Details` - which scanning the whole module (rather
      // than one guessed package) makes an ordinary occurrence, not a rare one.
      writer.write("package $packageName\n\n")
      writer.write("import $LOGGER_CLASS\n")
      writer.write("import $PARSER_CLASS\n\n")

      writer.write("/**\n")
      writer.write(" * A parser with every deep-link route this module declares already registered.\n")
      writer.write(" * @generated by DeepLinkProcessor\n")
      writer.write(" */\n")

      val parserSimpleName = PARSER_CLASS.substringAfterLast(".")
      val loggerSimpleName = LOGGER_CLASS.substringAfterLast(".")
      writer.write("internal fun perchModuleParser(\n")
      writer.write("  schemes: Set<String>,\n")
      writer.write("  hosts: Set<String> = emptySet(),\n")
      writer.write("  logger: $loggerSimpleName = $loggerSimpleName.None,\n")
      writer.write("): $parserSimpleName = $parserSimpleName(schemes, hosts, logger).apply {\n")
      // The manifest's own routes: a class whose @DeepLink path could not be read was skipped
      // when they were collected, so registering it here would emit a register<T>() no manifest
      // knows about.
      routes.sortedBy { it.routeClassName }.forEach { route ->
        writer.write("  register<${route.routeClassName}>()\n")
      }
      writer.write("}\n")
    }

    logger.info("DeepLinkProcessor: generated perchModuleParser() with ${routes.size} routes in $packageName")
  }

  private fun generateManifestFile(
    moduleId: String,
    routes: List<ManifestRoute>,
    routeSources: Array<KSFile>,
  ) {
    val manifestFile = codeGenerator.createNewFile(
      dependencies = Dependencies(aggregating = true, *routeSources),
      packageName = "",
      fileName = manifestFileNameFor(moduleId),
      extensionName = MANIFEST_FILE_EXTENSION,
    )

    OutputStreamWriter(manifestFile).use { writer ->
      routes.forEach { route -> writer.write(renderManifestLine(route) + "\n") }
    }

    logger.info("DeepLinkProcessor: generated manifest with ${routes.size} routes for $moduleId")
  }

  /** The @DeepLink annotation has a single "path" argument. */
  private fun deepLinkPath(classDecl: KSClassDeclaration): String? =
    deepLinkAnnotation(classDecl)?.arguments?.firstOrNull()?.value as? String
}
