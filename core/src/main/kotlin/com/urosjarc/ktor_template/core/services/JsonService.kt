package com.urosjarc.ktor_template.core.services

import com.urosjarc.ktor_template.core.serializers.BytesSerializer
import com.urosjarc.ktor_template.core.serializers.TimeZonedSerializer
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

public class JsonService(prettyPrint: Boolean = false) {
    public val module: Json =
        Json {
            serializersModule =
                SerializersModule {
                    contextual(ByteArray::class, BytesSerializer)
                    contextual(TimeZoned::class, TimeZonedSerializer)
                }
            this.encodeDefaults = true
            this.prettyPrint = prettyPrint
            this.isLenient = true
            this.allowSpecialFloatingPointValues = true
            this.ignoreUnknownKeys = true
        }

    public inline fun <reified T> encode(value: T): String {
        return this.module.encodeToString(value)
    }

    public inline fun <reified T> decode(value: String): T {
        return this.module.decodeFromString(value)
    }
}
