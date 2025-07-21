package com.solvesall.ktor_template.core.serializers

import com.solvesall.ktor_template.core.types.TimeZoned
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public object TimeZonedSerializer : KSerializer<TimeZoned> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TimeZoned", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): TimeZoned {
		return TimeZoned.parse(data = decoder.decodeString())!!
	}

	override fun serialize(encoder: Encoder, value: TimeZoned) {
		encoder.encodeString(value.toString(withTimeZone = true))
	}
}
