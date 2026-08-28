package com.example.sample.app

import com.example.sample.routes.HomeLink
import com.example.sample.routes.PaymentLink
import dev.carcara.perch.DeepLinkManager
import dev.carcara.perch.test.RecordingDeepLinkNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SamplePipelineTest {

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `the generated registration resolves a route declared in another module`() {
    val parsed = sampleParser().parse("sample://payments/abc123")

    assertIs<PaymentLink>(parsed)
    assertEquals("abc123", parsed.id)
  }

  @Test
  fun `every declared route is registered`() {
    val parser = sampleParser()

    assertIs<HomeLink>(parser.parse("sample://home"))
    assertIs<PaymentLink>(parser.parse("sample://payments/x"))
  }

  @Test
  fun `an unconfigured host does not resolve`() {
    assertNull(sampleParser().parse("https://evil.example/payments/abc123"))
  }

  @Test
  fun `the manager navigates a parsed url end to end`() = runTest {
    val navigator = RecordingDeepLinkNavigator()
    val dispatcher = UnconfinedTestDispatcher(testScheduler)
    val manager = DeepLinkManager(
      navigator = navigator,
      parser = sampleParser(),
      scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher),
      handlerDispatcher = dispatcher,
    ).also { it.setNavigationReady() }

    assertTrue(manager.handleDeepLink("sample://home"))
    advanceUntilIdle()

    assertIs<HomeLink>(navigator.lastPushedRoute)
    assertEquals(1, navigator.pushCallCount)
  }
}
