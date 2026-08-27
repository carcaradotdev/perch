package dev.carcara.perch.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/** Registered via `META-INF/services` for KSP to discover. */
public class DeepLinkProcessorProvider : SymbolProcessorProvider {

  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = DeepLinkProcessor(
    codeGenerator = environment.codeGenerator,
    logger = environment.logger,
    options = environment.options,
  )
}
