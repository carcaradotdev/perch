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
