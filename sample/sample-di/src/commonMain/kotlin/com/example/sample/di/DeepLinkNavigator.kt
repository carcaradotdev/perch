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

package com.example.sample.di

import com.example.sample.navigation.SampleRoute

/**
 * Where [DeepLinkRouter] sends a route once it has one.
 *
 * The shell implements this - Compose on Android, SwiftUI on iOS - and hands the implementation to
 * the graph at creation time. Which is the reason the interface exists: the router is
 * multiplatform and the navigation it drives is not.
 */
public interface DeepLinkNavigator {
  public fun goTo(route: SampleRoute)
}
