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
