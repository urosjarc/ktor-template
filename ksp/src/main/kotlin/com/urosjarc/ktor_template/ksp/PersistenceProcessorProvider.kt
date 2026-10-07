package com.urosjarc.ktor_template.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class PersistenceProcessorProvider : SymbolProcessorProvider {

    override fun create(
        environment: SymbolProcessorEnvironment,
    ): SymbolProcessor {
        environment.logger.warn(
            "PersistenceProcessorProvider CREATE " +
                    "generate.module=${environment.options["generate.module"]}"
        )

        if (environment.options["generate.module"] != "app") {
            return NoOpProcessor()
        }

        return PersistenceProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            processorOptions = environment.options,
        )
    }
}
