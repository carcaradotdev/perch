package dev.carcara.perch.metro

import dev.carcara.perch.DeepLinkAuthGate
import dev.carcara.perch.DeepLinkBootstrapState
import dev.carcara.perch.DeepLinkLockGate
import dev.carcara.perch.DeepLinkNavigator
import dev.carcara.perch.DeepLinkParser
import dev.carcara.perch.DeepLinkRouteHandler
import dev.carcara.perch.DeepLinkTarget
import dev.carcara.perch.test.FakeDeepLinkAuthGate
import dev.carcara.perch.test.FakeDeepLinkLockGate
import dev.carcara.perch.test.RecordingDeepLinkNavigator
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A minimal `@DependencyGraph` that merges [DeepLinkBindings] and
 * [DeepLinkRouteHandlerMapAccessor] the same way a real Metro consumer's `AppScope` graph would,
 * plus bound-instance test fakes for the five app-owned collaborators this module cannot provide
 * itself. Exists purely to prove `DeepLinkBindings` resolves as a graph — compiling a
 * `@ContributesTo` object on its own only records a contribution hint, it never runs Metro's
 * cycle/missing-binding/duplicate-binding checks.
 */
@DependencyGraph(AppScope::class)
internal interface DeepLinkBindingsTestGraph {
  val bootstrapState: DeepLinkBootstrapState
  val routeHandlers: Map<KClass<out DeepLinkTarget>, DeepLinkRouteHandler<*>>

  @DependencyGraph.Factory
  interface Factory {
    fun create(
      @Provides navigator: DeepLinkNavigator,
      @Provides parser: DeepLinkParser,
      @Provides scope: CoroutineScope,
      @Provides authGate: DeepLinkAuthGate,
      @Provides lockGate: DeepLinkLockGate,
    ): DeepLinkBindingsTestGraph
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkBindingsGraphTest {

  @BeforeTest
  fun setUp() {
    // DeepLinkManager's init launches on Dispatchers.Main; this module has no platform
    // main dispatcher in tests, so route it to the test scheduler.
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun TestScope.buildGraph(): DeepLinkBindingsTestGraph {
    val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    return createGraphFactory<DeepLinkBindingsTestGraph.Factory>().create(
      navigator = RecordingDeepLinkNavigator(),
      parser = DeepLinkParser(schemes = setOf("acme")),
      scope = CoroutineScope(backgroundScope.coroutineContext + testDispatcher),
      authGate = FakeDeepLinkAuthGate(),
      lockGate = FakeDeepLinkLockGate(),
    )
  }

  @Test
  fun `graph resolves DeepLinkBootstrapState from the five app-owned bindings`() = runTest {
    val graph = buildGraph()

    assertNotNull(graph.bootstrapState)
  }

  @Test
  fun `route-handler multibind resolves empty when nothing is contributed`() = runTest {
    val graph = buildGraph()

    assertTrue(graph.routeHandlers.isEmpty())
  }
}
