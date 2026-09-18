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

    import dev.carcara.perch.DeepLink

    @DeepLink("/home")
    class HomeLink

    @DeepLink("/payments/{id}")
    class PaymentLink(val id: String)

    class NotADeepLink
    """,
  )

  @Test
  fun `generates a parser factory carrying every annotated route`() {
    val result = compile(routeSource)

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/home/DeepLinkRegistration.kt")
    val text = generated.readText()

    assertTrue(text, text.contains("internal fun perchModuleParser("))
    assertTrue(text, text.contains("): DeepLinkParser = DeepLinkParser(schemes, hosts, logger).apply {"))
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
        "/home|com.acme.home.HomeLink|com.acme.home",
        "/payments/{id}|com.acme.home.PaymentLink|com.acme.home",
      ),
      lines,
    )
  }

  @Test
  fun `a Ktor Resource is not a deep link`() {
    // The reason Perch has an annotation of its own. An app that also uses Ktor's type-safe
    // client annotates HTTP resources with `@Resource`, and with no other rule to tell the two
    // apart, every one of those would become a route.
    val source = SourceFile.kotlin(
      "KtorResource.kt",
      """
      package com.acme.home

      import io.ktor.resources.Resource

      @Resource("/api/payments/{id}")
      class PaymentsApi(val id: String)
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

      import dev.carcara.perch.DeepLink

      @DeepLink("/thing/{id}")
      class First(val id: String)

      @DeepLink("/thing/{name}")
      class Second(val name: String)
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

      import dev.carcara.perch.DeepLink

      @DeepLink("/a/details")
      class Details
      """,
    )
    val second = SourceFile.kotlin(
      "Second.kt",
      """
      package com.acme.b

      import dev.carcara.perch.DeepLink

      @DeepLink("/b/details")
      class Details
      """,
    )

    val result = compile(first, second, outputPackage = "com.acme.merged")

    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    val generated = File(result.outputDirectory.parentFile, "ksp/sources/kotlin/com/acme/merged/DeepLinkRegistration.kt")
    val text = generated.readText()

    assertTrue(text.contains("register<com.acme.a.Details>()"))
    assertTrue(text.contains("register<com.acme.b.Details>()"))
  }
}
