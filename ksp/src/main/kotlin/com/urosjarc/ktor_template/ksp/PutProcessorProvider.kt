package com.urosjarc.ktor_template.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class PutProcessorProvider : SymbolProcessorProvider {

    override fun create(
        environment: SymbolProcessorEnvironment,
    ): SymbolProcessor {
        environment.logger.warn(
            "PutProcessorProvider CREATE " +
                    "generate.module=${environment.options["generate.module"]}"
        )

        if (environment.options["generate.module"] != "core") {
            return NoOpProcessor()
        }

        return PutProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}
