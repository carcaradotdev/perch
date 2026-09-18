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

package dev.carcara.perch.gradle

import org.gradle.api.GradleException
import java.util.Properties

/**
 * Version of this plugin build, and so of the `perch-ksp` artifact published alongside it.
 *
 * Read from a `perch.properties` resource that `perch-gradle-plugin/build.gradle.kts` generates
 * into the plugin jar, rather than hardcoded here, so a version bump moves one number in one file.
 * It is what makes [PerchExtension.processorCoordinates] default to a coordinate that resolves:
 * `dev.carcara.perch:perch-ksp` with no version does not, and a plugin cannot ask Gradle for its
 * own version at runtime.
 */
internal object PerchVersion {
  private const val RESOURCE_NAME = "perch.properties"
  private const val VERSION_KEY = "version"

  /** The version string, for example `0.1.0-SNAPSHOT`. */
  val value: String by lazy { read() }

  private fun read(): String {
    val properties = PerchVersion::class.java.getResourceAsStream(RESOURCE_NAME)?.use { stream ->
      Properties().apply { load(stream) }
    } ?: throw GradleException(missingVersionMessage("$RESOURCE_NAME is not in the plugin jar"))

    val version = properties.getProperty(VERSION_KEY)
    if (version.isNullOrBlank()) {
      throw GradleException(missingVersionMessage("$RESOURCE_NAME carries no '$VERSION_KEY'"))
    }
    return version
  }

  private fun missingVersionMessage(cause: String): String =
    "dev.carcara.perch: cannot determine the Perch version because $cause, so the default " +
      "KSP processor coordinate would not resolve. This is a packaging fault in the plugin " +
      "itself; work around it by setting `perch.processorCoordinates` to an explicit " +
      "coordinate, for example \"dev.carcara.perch:perch-ksp:<version>\"."
}
