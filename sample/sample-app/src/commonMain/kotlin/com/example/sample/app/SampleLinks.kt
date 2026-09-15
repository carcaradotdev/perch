package com.example.sample.app

/**
 * The links the sample demonstrates.
 *
 * Here rather than in either shell because both read from it: the iOS picker lists [all], and both
 * pickers start on [DEFAULT]. A literal repeated in two shells drifts the first time a route
 * changes shape, and nothing would catch it - the two would quietly stop demonstrating the same
 * link.
 */
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
