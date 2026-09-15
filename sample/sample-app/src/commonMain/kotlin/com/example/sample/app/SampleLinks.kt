package com.example.sample.app

/** The links the sample demonstrates, here rather than in either shell because both read them. */
public object SampleLinks {

  /** What a picker starts on. */
  public const val DEFAULT: String = "sample://payments/abc123"

  /** Every link worth trying, in the order the iOS picker lists them. */
  public val all: List<String> = listOf(
    "sample://payments/abc123",
    "sample://payment-approvals",
    "sample://home",
    "https://sample.example/payments/from-the-web",
    "https://evil.example/payments/abc123",
    "sample://nothing-matches-this",
  )
}
