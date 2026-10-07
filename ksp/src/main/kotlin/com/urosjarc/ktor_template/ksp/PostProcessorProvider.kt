package com.urosjarc.ktor_template.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class PostProcessorProvider : SymbolProcessorProvider {

    override fun create(
        environment: SymbolProcessorEnvironment,
    ): SymbolProcessor {
        environment.logger.warn(
            "PostProcessorProvider CREATE " +
                    "generate.module=${environment.options["generate.module"]}"
        )

        if (environment.options["generate.module"] != "core") {
            return NoOpProcessor()
        }

        return PostProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
    }
}
