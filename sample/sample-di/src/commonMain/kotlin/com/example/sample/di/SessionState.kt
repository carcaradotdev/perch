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

/**
 * Whether anyone is signed in, for the handlers that gate on it.
 *
 * Nothing in the sample authenticates. What matters is that a feature's handler can ask for this in
 * its constructor and the graph supplies it, with no shell passing it down by hand.
 */
public interface SessionState {
  public val signedIn: Boolean
}
