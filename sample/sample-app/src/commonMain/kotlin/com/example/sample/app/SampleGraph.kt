package com.example.sample.app

import com.example.sample.di.AppScope
import com.example.sample.di.DeepLinkNavigator
import com.example.sample.di.DeepLinkRouter
import com.example.sample.di.SessionState
import dev.carcara.perch.DeepLinkParser
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The one place the app says what its parser is.
 *
 * This is the whole of Perch's presence in a graph: a provider whose body is the generated
 * aggregate. Everything downstream asks for a `DeepLinkParser` and never learns which features
 * exist, or that a code generator was involved at all.
 */
@BindingContainer
@ContributesTo(AppScope::class)
public object DeepLinkBindings {

  @Provides
  public fun provideParser(): DeepLinkParser = sampleParser()
}

/**
 * The application graph, assembled at compile time from everything contributed to [AppScope].
 *
 * It names one type it can hand out and two it needs from the shell. What it does not name is the
 * payments handler, which reaches it from a module this file has never heard of.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
public interface SampleGraph {

  public val router: DeepLinkRouter

  /**
   * What the shell brings. Metro puts a companion implementing this on [SampleGraph], so the call
   * is `SampleGraph.create(navigator, session)` - from Swift too, where the reified
   * `createGraphFactory` would not have survived the crossing to Objective-C.
   */
  @DependencyGraph.Factory
  public fun interface Factory {
    public fun create(
      @Provides navigator: DeepLinkNavigator,
      @Provides session: SessionState,
    ): SampleGraph
  }
}
