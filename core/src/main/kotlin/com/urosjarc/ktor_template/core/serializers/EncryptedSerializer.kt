package com.urosjarc.ktor_template.core.serializers

import com.urosjarc.ktor_template.core.types.Encrypted
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

public object EncryptedSerializer : KSerializer<Encrypted> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Encrypted", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): Encrypted {
		return Encrypted(value = decoder.decodeString().encodeToByteArray())
	}

	override fun serialize(encoder: Encoder, value: Encrypted) {
		encoder.encodeString(value.decrypt())
	}
}
