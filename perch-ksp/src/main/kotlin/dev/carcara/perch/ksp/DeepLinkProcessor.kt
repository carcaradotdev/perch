package dev.carcara.perch.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import java.io.OutputStreamWriter

/**
 * KSP processor that generates a `registerDeepLinks()` extension on the module's parser.
 *
 * The rule is one sentence: a class in this module's own sources, annotated
 * `@dev.carcara.perch.DeepLink`, that implements the configured target base class, is a route.
 * The processor never looks outside the module it runs on.
 */
internal class DeepLinkProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val options: Map<String, String>,
) : SymbolProcessor {

  private companion object {
    private const val DEEP_LINK_ANNOTATION = "dev.carcara.perch.DeepLink"
    private const val DEFAULT_TARGET_BASE_CLASS = "dev.carcara.perch.DeepLinkTarget"
    private const val DEFAULT_PARSER_CLASS = "dev.carcara.perch.DeepLinkParser"
  }

  private val targetBaseClass = options["perch.targetBaseClass"] ?: DEFAULT_TARGET_BASE_CLASS
  private val parserClass = options["perch.parserClass"] ?: DEFAULT_PARSER_CLASS

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

    generateRegistrationFile(outputPackage, routeClasses.toList())

    return emptyList()
  }

  private fun collectRoutes(
    classDecl: KSClassDeclaration,
    result: MutableSet<KSClassDeclaration>,
  ) {
    if (hasDeepLinkAnnotation(classDecl) && extendsRoute(classDecl)) {
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

  private fun extendsRoute(classDecl: KSClassDeclaration): Boolean = classDecl.superTypes.any { superType ->
    isOrExtendsRoute(superType.resolve())
  }

  private fun isOrExtendsRoute(type: KSType): Boolean {
    val declaration = type.declaration as? KSClassDeclaration ?: return false
    val qualifiedName = declaration.qualifiedName?.asString()

    if (qualifiedName == targetBaseClass) {
      return true
    }

    return declaration.superTypes.any { superType ->
      isOrExtendsRoute(superType.resolve())
    }
  }

  private fun generateRegistrationFile(packageName: String, routeClasses: List<KSClassDeclaration>) {
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
      routeInfoList.add(RouteInfo(path, routeName, packageName))
    }

    generateManifestFile(packageName, routeInfoList)

    val file = codeGenerator.createNewFile(
      dependencies = Dependencies(aggregating = false),
      packageName = packageName,
      fileName = "DeepLinkRegistration",
    )

    OutputStreamWriter(file).use { writer ->
      writer.write("package $packageName\n\n")
      // Only the parser needs an import: the receiver of the extension below. Route type
      // arguments are written fully qualified, on purpose. Importing them by simple name would
      // collide the moment two routes in this module share a simple name from different
      // packages - `com.acme.a.Details` and `com.acme.b.Details` - which scanning the whole
      // module (rather than one guessed package) makes an ordinary occurrence, not a rare one.
      writer.write("import $parserClass\n")

      writer.write("\n")
      writer.write("/**\n")
      writer.write(" * Generated function to register all deep link routes for this module.\n")
      writer.write(" * @generated by DeepLinkProcessor\n")
      writer.write(" */\n")

      val parserSimpleName = parserClass.substringAfterLast(".")
      writer.write("internal fun $parserSimpleName.registerDeepLinks() {\n")
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

    // Format: path|routeClassName|moduleName
    val manifestFile = codeGenerator.createNewFile(
      dependencies = Dependencies(aggregating = false),
      packageName = "",
      fileName = "perch-manifest-${packageName.replace(".", "-")}",
      extensionName = "txt",
    )

    OutputStreamWriter(manifestFile).use { writer ->
      routes.forEach { route ->
        writer.write("${route.path}|${route.routeClassName}|${route.moduleName}\n")
      }
    }

    logger.info("DeepLinkProcessor: generated manifest with ${routes.size} routes for $packageName")
  }

  private data class RouteInfo(
    val path: String,
    val routeClassName: String,
    val moduleName: String,
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
