package dev.carcara.perch.gradle

import org.gradle.api.attributes.Attribute

/** Marks the producer and consumer configurations that carry deep-link route manifests. */
internal val PERCH_MANIFEST_ATTRIBUTE: Attribute<String> =
  Attribute.of("dev.carcara.perch.manifest", String::class.java)
