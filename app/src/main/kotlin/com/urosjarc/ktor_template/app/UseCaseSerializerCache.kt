package com.urosjarc.ktor_template.app

import com.urosjarc.ktor_template.core.services.JsonService
import com.urosjarc.ktor_template.core.usecases.UseCase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.allSupertypes

public object UseCaseSerializerCache {
    private val cache = ConcurrentHashMap<KClass<*>, UseCaseSerializers>()

    public data class UseCaseSerializers(
        val input: KSerializer<Any>,
        val output: KSerializer<Any>,
    )

    @Suppress("UNCHECKED_CAST")
    public fun get(useCase: UseCase<*, *>): UseCaseSerializers = cache.computeIfAbsent(useCase::class) {
        val supertype =
            useCase::class.allSupertypes.firstOrNull { it.classifier == UseCase::class }
                ?: error("${useCase::class.qualifiedName} does not extend UseCase<I, O>.")

        val inputType: KType = supertype.arguments[0].type
            ?: error("Cannot determine input type of ${useCase::class.qualifiedName}.")
        val outputType: KType = supertype.arguments[1].type
            ?: error("Cannot determine output type of ${useCase::class.qualifiedName}.")

        UseCaseSerializers(
            input = JsonService.module.serializersModule.serializer(inputType) as KSerializer<Any>,
            output = JsonService.module.serializersModule.serializer(outputType) as KSerializer<Any>,
        )
    }
}
