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

package com.example.sample.home.api

import com.example.sample.navigation.SampleRoute
import dev.carcara.perch.DeepLink

/**
 * The link that opens the app on Home.
 *
 * A `data object` because the route carries nothing: there is one Home, and the path has no
 * placeholders to fill. `@DeepLink` is the whole contract Perch asks for - no `@Serializable`,
 * because `@DeepLink` is `@MetaSerializable` and the compiler plugin generates the serialiser from
 * it alone.
 */
@DeepLink("/home")
public data object HomeDeepLink : SampleRoute
