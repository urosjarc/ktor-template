package com.urosjarc.ktor_template.core.serializers

import com.urosjarc.ktor_template.core.types.Hashed
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public object HashedSerializer : KSerializer<Hashed> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Hashed", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): Hashed {
		return Hashed(value = decoder.decodeString().encodeToByteArray())
	}

	override fun serialize(encoder: Encoder, value: Hashed) {
		encoder.encodeString("")
	}
}
