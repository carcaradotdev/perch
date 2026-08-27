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

class DeepLinkProcessorTest {

  @get:Rule val workingDir = TemporaryFolder()

  private fun compile(vararg sources: SourceFile, outputPackage: String = "com.acme.home") =
    KotlinCompilation().apply {
      this.sources = sources.toList()
      workingDir = this@DeepLinkProcessorTest.workingDir.root
      inheritClassPath = true
      // perch-core is built with a JVM 21 toolchain, and its inline `register<T>()` cannot
      // inline into bytecode compiled for kctfork's default JVM 1.8 target.
      jvmTarget = "21"
      configureKsp {
        symbolProcessorProviders += DeepLinkProcessorProvider()
        processorOptions["perch.outputPackage"] = outputPackage
      }
    }.compile()

  private val routeSource = SourceFile.kotlin(
    "Routes.kt",
    """
    package com.acme.home

    import dev.carcara.perch.DeepLinkTarget
    import io.ktor.resources.Resource
    import kotlinx.serialization.Serializable

    @Serializable
    @Resource("/home")
    class HomeLink : DeepLinkTarget {
      override val requiresAuth: Boolean get() = false
    }

    @Serializable
    @Resource("/payments/{id}")
    class PaymentLink(val id: String) : DeepLinkTarget {
      override val requiresAuth: Boolean get() = true
    }

    class NotADeepLink
    """,
  )

  @Test
  fun `generates a registration extension for every annotated target`() {
    val result = compile(routeSource)

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/home/DeepLinkRegistration.kt")
    val text = generated.readText()

    assertTrue(text.contains("internal fun DeepLinkParser.registerDeepLinks()"))
    assertTrue(text.contains("register<HomeLink>()"))
    assertTrue(text.contains("register<PaymentLink>()"))
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
        "/home|com.acme.home.HomeLink|com.acme.home",
        "/payments/{id}|com.acme.home.PaymentLink|com.acme.home",
      ),
      lines,
    )
  }

  @Test
  fun `a class with Resource but not DeepLinkTarget is ignored`() {
    val source = SourceFile.kotlin(
      "Other.kt",
      """
      package com.acme.home

      import io.ktor.resources.Resource
      import kotlinx.serialization.Serializable

      @Serializable
      @Resource("/nope")
      class NotATarget
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

      import dev.carcara.perch.DeepLinkTarget
      import io.ktor.resources.Resource
      import kotlinx.serialization.Serializable

      @Serializable @Resource("/thing/{id}")
      class First(val id: String) : DeepLinkTarget { override val requiresAuth get() = false }

      @Serializable @Resource("/thing/{name}")
      class Second(val name: String) : DeepLinkTarget { override val requiresAuth get() = false }
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
