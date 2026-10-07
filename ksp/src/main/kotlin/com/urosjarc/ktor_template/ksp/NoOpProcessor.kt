package com.urosjarc.ktor_template.ksp

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

class NoOpProcessor : SymbolProcessor {

    override fun process(
        resolver: Resolver,
    ): List<KSAnnotated> =
        emptyList()
}
