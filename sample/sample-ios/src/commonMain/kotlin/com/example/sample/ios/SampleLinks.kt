package com.example.sample.ios

/**
 * The links both shells offer, so the Android picker and the iOS one demonstrate the same set.
 *
 * This module has a source file at all because a Kotlin/Native framework with no source compiles
 * to nothing: `linkDebugFrameworkIosSimulatorArm64` reports NO-SOURCE and produces no binary, no
 * matter how many dependencies the module exports. So the one file it needs may as well carry
 * something the Swift side would otherwise hard-code.
 */
public object SampleLinks {

  /** What the picker starts on. */
  public const val DEFAULT: String = "sample://payments/abc123"

  /** Every link worth trying, in the order the picker lists them. */
  public val all: List<String> = listOf(
    "sample://payments/abc123",
    "sample://payment-approvals",
    "sample://home",
    "https://sample.example/payments/from-the-web",
    "https://evil.example/payments/abc123",
    "sample://nothing-matches-this",
  )
}
