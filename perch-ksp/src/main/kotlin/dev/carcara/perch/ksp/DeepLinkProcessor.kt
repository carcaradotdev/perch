package dev.carcara.perch.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.io.OutputStreamWriter

/**
 * KSP processor that generates a `registerDeepLinks()` extension on the module's parser.
 *
 * The rule is one sentence: a class in this module's own sources annotated
 * `@dev.carcara.perch.DeepLink` is a route. The processor never looks outside the module it runs
 * on.
 *
 * `DeepLinkParser` is generic over the app's own route type, so the generated extension has to name
 * that type: `DeepLinkParser<com.acme.Route>.registerDeepLinks()`. It is read off the routes rather
 * than configured — the one supertype every route in the module shares. `perch.targetBaseClass`
 * overrides that for a module whose routes share more than one.
 */
internal class DeepLinkProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val options: Map<String, String>,
) : SymbolProcessor {

  private companion object {
    private const val DEEP_LINK_ANNOTATION = "dev.carcara.perch.DeepLink"
    private const val DEFAULT_PARSER_CLASS = "dev.carcara.perch.DeepLinkParser"
    private const val ANY = "kotlin.Any"
  }

  private val configuredBaseClass = options["perch.targetBaseClass"]?.takeIf { it.isNotBlank() }
  private val parserClass = options["perch.parserClass"] ?: DEFAULT_PARSER_CLASS

  /** Every supertype the walk has seen, by name, so a candidate's own hierarchy can be asked for. */
  private val declarationsByName = mutableMapOf<String, KSClassDeclaration>()

  private var processed = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (processed) return emptyList()
    processed = true

    val outputPackage = options["perch.outputPackage"]
    if (outputPackage.isNullOrBlank()) return emptyList()

    val routeClasses = mutableSetOf<KSClassDeclaration>()
    resolver.getAllFiles().forEach { file ->
      file.declarations.filterIsInstance<KSClassDeclaration>().forEach { classDecl ->
        collectRoutes(classDecl, routeClasses)
      }
    }

    if (routeClasses.isEmpty()) return emptyList()

    val baseClass = configuredBaseClass ?: inferBaseClass(routeClasses) ?: return emptyList()
    if (!routesImplement(baseClass, routeClasses)) return emptyList()

    generateRegistrationFile(outputPackage, baseClass, routeClasses.toList())

    return emptyList()
  }

  private fun collectRoutes(
    classDecl: KSClassDeclaration,
    result: MutableSet<KSClassDeclaration>,
  ) {
    if (hasDeepLinkAnnotation(classDecl)) {
      result.add(classDecl)
    }

    classDecl.declarations
      .filterIsInstance<KSClassDeclaration>()
      .forEach { nested ->
        collectRoutes(nested, result)
      }
  }

  private fun hasDeepLinkAnnotation(classDecl: KSClassDeclaration): Boolean = classDecl.annotations.any { annotation ->
    annotation.annotationType.resolve().declaration.qualifiedName?.asString() == DEEP_LINK_ANNOTATION
  }

  /**
   * Every supertype of [classDecl], transitively. `kotlin.Any` is left out: every class has it, so
   * it would always be the shared answer, and `DeepLinkParser<Any>` accepts anything — which is
   * the type safety the base class exists to keep.
   */
  private fun supertypesOf(classDecl: KSClassDeclaration): Set<String> {
    val result = mutableSetOf<String>()
    classDecl.superTypes.forEach { reference ->
      val superDeclaration = reference.resolve().declaration as? KSClassDeclaration ?: return@forEach
      val name = superDeclaration.qualifiedName?.asString() ?: return@forEach
      if (name == ANY || !result.add(name)) return@forEach
      declarationsByName[name] = superDeclaration
      result += supertypesOf(superDeclaration)
    }
    return result
  }

  /**
   * The route type the module's routes share, which becomes the parser's type argument.
   *
   * When several are shared — a route implementing both `Route` and a marker interface — the most
   * specific one wins, meaning the candidate that is itself a subtype of all the others. Anything
   * genuinely ambiguous is reported rather than guessed.
   */
  private fun inferBaseClass(routes: Set<KSClassDeclaration>): String? {
    val shared = routes.map { supertypesOf(it) }.reduce { common, next -> common intersect next }

    if (shared.isEmpty()) {
      logger.error(
        "Perch: the @DeepLink routes in this module share no common supertype, so there is no " +
          "type to declare the generated DeepLinkParser<...>.registerDeepLinks() on. Give them " +
          "one — a sealed interface over your routes is the usual shape — or name it with " +
          "perch { targetBaseClass.set(\"com.acme.Route\") }.",
        routes.first(),
      )
      return null
    }

    val mostSpecific = shared.filter { candidate ->
      val candidateSupertypes = declarationsByName[candidate]?.let { supertypesOf(it) }.orEmpty()
      shared.all { other -> other == candidate || other in candidateSupertypes }
    }

    if (mostSpecific.size != 1) {
      logger.error(
        "Perch: the @DeepLink routes in this module share ${shared.size} supertypes " +
          "(${shared.sorted().joinToString()}) and none of them is the most specific, so the " +
          "generated registration cannot pick one. Name it with " +
          "perch { targetBaseClass.set(\"com.acme.Route\") }.",
        routes.first(),
      )
      return null
    }

    return mostSpecific.single()
  }

  /**
   * Checked only for a configured base class: an inferred one holds by construction. Without this,
   * a route outside the configured type fails in the generated file instead, with a bounds error
   * pointing at code nobody wrote.
   */
  private fun routesImplement(baseClass: String, routes: Set<KSClassDeclaration>): Boolean {
    val strays = routes.filterNot { baseClass in supertypesOf(it) }
    if (strays.isEmpty()) return true

    strays.forEach { stray ->
      logger.error(
        "Perch: ${stray.qualifiedName?.asString()} is annotated @DeepLink but does not implement " +
          "$baseClass, the configured perch.targetBaseClass.",
        stray,
      )
    }
    return false
  }

  private fun generateRegistrationFile(
    packageName: String,
    baseClass: String,
    routeClasses: List<KSClassDeclaration>,
  ) {
    val routeInfoList = mutableListOf<RouteInfo>()
    val registeredRoutes = mutableListOf<Pair<String, String>>()

    routeClasses.forEach { classDecl ->
      val path = deepLinkPath(classDecl) ?: return@forEach
      val routeName = classDecl.qualifiedName?.asString() ?: return@forEach

      for ((existingPath, existingRoute) in registeredRoutes) {
        if (patternsConflict(path, existingPath)) {
          // Reporting through the logger, rather than throwing, is what fails the KSP round with
          // a COMPILATION_ERROR instead of an uncaught-exception INTERNAL_ERROR: KSP fails the
          // build once any error is logged, without needing the round to unwind via an exception.
          logger.error(
            "Deep link collision detected. Pattern '$path' conflicts with '$existingPath', " +
              "already registered by '$existingRoute', so '$routeName' cannot be registered.",
            classDecl,
          )
          return
        }
      }
      registeredRoutes.add(path to routeName)
      routeInfoList.add(RouteInfo(path, routeName, packageName, baseClass))
    }

    generateManifestFile(packageName, routeInfoList)

    val file = codeGenerator.createNewFile(
      dependencies = Dependencies(aggregating = false),
      packageName = packageName,
      fileName = "DeepLinkRegistration",
    )

    OutputStreamWriter(file).use { writer ->
      writer.write("package $packageName\n\n")
      // Only the parser needs an import: the receiver of the extension below. The base class and
      // the route type arguments are written fully qualified, on purpose. Importing them by simple
      // name would collide the moment two routes in this module share a simple name from different
      // packages - `com.acme.a.Details` and `com.acme.b.Details` - which scanning the whole
      // module (rather than one guessed package) makes an ordinary occurrence, not a rare one.
      writer.write("import $parserClass\n")

      writer.write("\n")
      writer.write("/**\n")
      writer.write(" * Generated function to register all deep link routes for this module.\n")
      writer.write(" * @generated by DeepLinkProcessor\n")
      writer.write(" */\n")

      val parserSimpleName = parserClass.substringAfterLast(".")
      writer.write("internal fun $parserSimpleName<$baseClass>.registerDeepLinks() {\n")
      // routeInfoList, not routeClasses: a class whose @DeepLink path could not be read was
      // skipped above and has no manifest entry, so registering it here would emit a
      // register<T>() the manifest does not know about.
      routeInfoList.sortedBy { it.routeClassName }.forEach { route ->
        writer.write("  register<${route.routeClassName}>()\n")
      }
      writer.write("}\n")
    }

    logger.info("DeepLinkProcessor: generated registration for ${routeInfoList.size} routes in $packageName")
  }

  private fun generateManifestFile(packageName: String, routes: List<RouteInfo>) {
    if (routes.isEmpty()) return

    // Format: path|routeClassName|moduleName|baseClass. The base class travels with the routes
    // because the aggregator has manifests and no type information of its own, and it needs the
    // same type to declare registerAllDeepLinks() on.
    val manifestFile = codeGenerator.createNewFile(
      dependencies = Dependencies(aggregating = false),
      packageName = "",
      fileName = "perch-manifest-${packageName.replace(".", "-")}",
      extensionName = "txt",
    )

    OutputStreamWriter(manifestFile).use { writer ->
      routes.forEach { route ->
        writer.write("${route.path}|${route.routeClassName}|${route.moduleName}|${route.baseClass}\n")
      }
    }

    logger.info("DeepLinkProcessor: generated manifest with ${routes.size} routes for $packageName")
  }

  private data class RouteInfo(
    val path: String,
    val routeClassName: String,
    val moduleName: String,
    val baseClass: String,
  )

  private fun deepLinkPath(classDecl: KSClassDeclaration): String? {
    val deepLinkAnnotation = classDecl.annotations.find { annotation ->
      annotation.annotationType.resolve().declaration.qualifiedName?.asString() == DEEP_LINK_ANNOTATION
    } ?: return null

    // The @DeepLink annotation has a single "path" argument.
    val pathArg = deepLinkAnnotation.arguments.firstOrNull()
    return pathArg?.value as? String
  }
}
