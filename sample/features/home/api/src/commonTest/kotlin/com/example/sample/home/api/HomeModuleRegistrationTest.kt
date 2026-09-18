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

import kotlin.test.Test
import kotlin.test.assertIs

/**
 * The single-module shape: a feature that declares links can resolve its own, with no aggregator
 * anywhere in the build.
 *
 * `perchModuleParser()` is what the Perch KSP processor writes into this module's own
 * `commonMain`, so this file does not compile unless `dev.carcara.perch` has put the generated
 * directory on the compile path. `sample-app` covers the other shape, where an aggregator collects
 * this module's manifest along with the payments feature's.
 */
class HomeModuleRegistrationTest {

  @Test
  fun `perchModuleParser carries the link this feature declares`() {
    val parser = perchModuleParser(schemes = setOf("sample"))

    assertIs<HomeDeepLink>(parser.parse("sample://home"))
  }
}
