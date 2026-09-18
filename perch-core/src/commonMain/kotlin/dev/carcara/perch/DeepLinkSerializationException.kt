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

/*
 * Derived from io/ktor/resources/ResourceSerializationException.kt in Ktor.
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by
 * the Apache 2.0 license. See NOTICE.
 */

package dev.carcara.perch

/**
 * Thrown when a route cannot be turned into a URL or read back out of one: a class with no
 * [DeepLink] annotation, more than one parent property, or a path placeholder with no value to
 * fill it.
 *
 * [DeepLinkParser.parse] never throws this — an input that does not decode is simply not a match.
 * [DeepLinkParser.toUrl] does, because a route it cannot render is a programming error.
 */
public class DeepLinkSerializationException(message: String) : Exception(message)
