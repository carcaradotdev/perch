@file:OptIn(ExperimentalCompilerApi::class)

package dev.carcara.perch.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.io.File

private const val APP_ROUTE = "com.acme.routes.AppRoute"

class DeepLinkProcessorTest {

  @get:Rule val workingDir = TemporaryFolder()

  /** The consuming app's own route type. Perch ships none, so every fixture brings this along. */
  private val baseSource = SourceFile.kotlin(
    "AppRoute.kt",
    """
    package com.acme.routes

    interface AppRoute
    """,
  )

  private fun compile(
    vararg sources: SourceFile,
    outputPackage: String = "com.acme.home",
    targetBaseClass: String = "",
  ) =
    KotlinCompilation().apply {
      this.sources = listOf(baseSource) + sources
      workingDir = this@DeepLinkProcessorTest.workingDir.root
      inheritClassPath = true
      // perch-core is built with a JVM 21 toolchain, and its inline `register<T>()` cannot
      // inline into bytecode compiled for kctfork's default JVM 1.8 target.
      jvmTarget = "21"
      configureKsp {
        symbolProcessorProviders += DeepLinkProcessorProvider()
        processorOptions["perch.outputPackage"] = outputPackage
        processorOptions["perch.targetBaseClass"] = targetBaseClass
      }
    }.compile()

  private val routeSource = SourceFile.kotlin(
    "Routes.kt",
    """
    package com.acme.home

    import com.acme.routes.AppRoute
    import dev.carcara.perch.DeepLink

    @DeepLink("/home")
    class HomeLink : AppRoute

    @DeepLink("/payments/{id}")
    class PaymentLink(val id: String) : AppRoute

    class NotADeepLink
    """,
  )

  @Test
  fun `generates a registration extension for every annotated target`() {
    val result = compile(routeSource)

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/home/DeepLinkRegistration.kt")
    val text = generated.readText()

    assertTrue(text.contains("internal fun DeepLinkParser<$APP_ROUTE>.registerDeepLinks()"))
    assertTrue(text.contains("register<com.acme.home.HomeLink>()"))
    assertTrue(text.contains("register<com.acme.home.PaymentLink>()"))
    assertTrue(!text.contains("NotADeepLink"))
  }

  @Test
  fun `writes one manifest line per route`() {
    val result = compile(routeSource)

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val manifest = File(
      result.outputDirectory.parentFile,
      "ksp/sources/resources/perch-manifest-com-acme-home.txt",
    )
    val lines = manifest.readLines().filter { it.isNotBlank() }.sorted()

    assertEquals(
      listOf(
        "/home|com.acme.home.HomeLink|com.acme.home|$APP_ROUTE",
        "/payments/{id}|com.acme.home.PaymentLink|com.acme.home|$APP_ROUTE",
      ),
      lines,
    )
  }

  @Test
  fun `routes sharing no supertype fail the compilation by name`() {
    val source = SourceFile.kotlin(
      "Other.kt",
      """
      package com.acme.home

      import dev.carcara.perch.DeepLink

      @DeepLink("/nope")
      class NotATarget
      """,
    )

    val result = compile(source)

    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    assertTrue(result.messages.contains("share no common supertype"))
  }

  @Test
  fun `the most specific shared supertype wins over the ones above it`() {
    val hierarchy = SourceFile.kotlin(
      "Hierarchy.kt",
      """
      package com.acme.home

      import com.acme.routes.AppRoute
      import dev.carcara.perch.DeepLink

      interface PaymentRoute : AppRoute

      @DeepLink("/payments/{id}")
      class PaymentLink(val id: String) : PaymentRoute

      @DeepLink("/history")
      class HistoryLink : PaymentRoute
      """,
    )

    val result = compile(hierarchy)

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/home/DeepLinkRegistration.kt")

    assertTrue(
      generated.readText()
        .contains("internal fun DeepLinkParser<com.acme.home.PaymentRoute>.registerDeepLinks()"),
    )
  }

  @Test
  fun `an explicit target base class overrides what would be inferred`() {
    val result = compile(routeSource, targetBaseClass = APP_ROUTE)

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/home/DeepLinkRegistration.kt")

    assertTrue(generated.readText().contains("internal fun DeepLinkParser<$APP_ROUTE>.registerDeepLinks()"))
  }

  @Test
  fun `a route outside an explicit target base class is named`() {
    val source = SourceFile.kotlin(
      "Stray.kt",
      """
      package com.acme.home

      import dev.carcara.perch.DeepLink

      @DeepLink("/stray")
      class StrayLink
      """,
    )

    val result = compile(source, targetBaseClass = APP_ROUTE)

    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    assertTrue(result.messages.contains("com.acme.home.StrayLink is annotated @DeepLink but does not implement"))
  }

  @Test
  fun `a Ktor Resource on the route type is not a deep link`() {
    // The reason Perch has an annotation of its own. An app that also uses Ktor's type-safe
    // client annotates HTTP resources with `@Resource`; those are not deep links, and the
    // processor must not mistake one for a route even when it implements the app's route type.
    // Nothing is generated because nothing in the module carries @DeepLink.
    val source = SourceFile.kotlin(
      "KtorResource.kt",
      """
      package com.acme.home

      import com.acme.routes.AppRoute
      import io.ktor.resources.Resource

      @Resource("/api/payments/{id}")
      class PaymentsApi(val id: String) : AppRoute
      """,
    )

    val result = compile(source)

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/home/DeepLinkRegistration.kt")
    assertTrue(!generated.exists())
  }

  @Test
  fun `two routes with conflicting patterns fail the compilation`() {
    val source = SourceFile.kotlin(
      "Clash.kt",
      """
      package com.acme.home

      import com.acme.routes.AppRoute
      import dev.carcara.perch.DeepLink

      @DeepLink("/thing/{id}")
      class First(val id: String) : AppRoute

      @DeepLink("/thing/{name}")
      class Second(val name: String) : AppRoute
      """,
    )

    val result = compile(source)

    assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
    assertTrue(result.messages.contains("Deep link collision"))
  }

  @Test
  fun `a blank output package generates nothing`() {
    val result = compile(routeSource, outputPackage = "")

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/home/DeepLinkRegistration.kt")
    assertTrue(!generated.exists())
    val manifest = File(
      result.outputDirectory.parentFile,
      "ksp/sources/resources/perch-manifest-com-acme-home.txt",
    )
    assertTrue(!manifest.exists())
  }

  @Test
  fun `two routes with the same simple name in different packages both register fully qualified`() {
    val first = SourceFile.kotlin(
      "First.kt",
      """
      package com.acme.a

      import com.acme.routes.AppRoute
      import dev.carcara.perch.DeepLink

      @DeepLink("/a/details")
      class Details : AppRoute
      """,
    )
    val second = SourceFile.kotlin(
      "Second.kt",
      """
      package com.acme.b

      import com.acme.routes.AppRoute
      import dev.carcara.perch.DeepLink

      @DeepLink("/b/details")
      class Details : AppRoute
      """,
    )

    val result = compile(first, second, outputPackage = "com.acme.merged")

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/merged/DeepLinkRegistration.kt")
    val text = generated.readText()

    assertTrue(text.contains("register<com.acme.a.Details>()"))
    assertTrue(text.contains("register<com.acme.b.Details>()"))
  }

  @Test
  fun `the processor pattern check agrees with the core one`() {
    val cases = listOf(
      "/a/b" to "/a/b/",
      "/a/list" to "/a/{id}",
      "/a/{id}" to "/a/{name}",
      "/a/list" to "/a/details",
      "/a/list" to "/a/list/details",
      "/a/{rest...}" to "/a/b/c",
      "/a" to "/a/{opt?}",
    )

    cases.forEach { (left, right) ->
      assertEquals(
        "disagreement on '$left' vs '$right'",
        dev.carcara.perch.patternsConflict(left, right),
        patternsConflict(left, right),
      )
    }
  }
}
