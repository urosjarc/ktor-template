package com.urosjarc.ktor_template.core.services

import com.urosjarc.ktor_template.core.serializers.BytesSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

public class JsonService(prettyPrint: Boolean = false) {
	public val module: Json =
		Json {
			serializersModule =
				SerializersModule {
					BytesSerializer
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
