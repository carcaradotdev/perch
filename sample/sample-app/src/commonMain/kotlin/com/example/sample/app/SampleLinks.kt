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
