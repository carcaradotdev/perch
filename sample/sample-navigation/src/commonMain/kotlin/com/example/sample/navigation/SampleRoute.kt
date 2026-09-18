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

package com.example.sample.navigation

import androidx.navigation3.runtime.NavKey

/**
 * The supertype every route in this sample shares.
 *
 * Perch requires nothing of a route class, which is what leaves this slot free for the app to
 * spend on its own. Two things are spent here:
 *
 * - `parse` returns `Any?`, so the app narrows once with `as? SampleRoute` and everything past
 *   that point is typed. `null` from the cast is the app's single "not one of ours" branch.
 * - It extends `NavKey`, so a parsed route is already what Navigation 3's back stack accepts.
 *
 * It is deliberately not `sealed`, and it could not be: Kotlin permits implementations of a sealed
 * type only inside the module that declares it, and the whole point of the layout below is that
 * each feature declares its own links. So narrowing buys typing, not an exhaustive `when` - a
 * `when` over routes still needs an `else`, and adding a link to a feature will not fail the app's
 * build. An app that keeps every route in one module can seal this and get exhaustiveness too.
 *
 * Neither is Perch's idea, and a different app would spend the slot differently or not at all.
 * This module is the sample's equivalent of the module that would own such a type in a real app -
 * below the features, so every feature can implement it, and above nothing.
 */
public interface SampleRoute : NavKey
