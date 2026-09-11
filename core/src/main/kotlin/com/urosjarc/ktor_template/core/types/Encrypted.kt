package com.urosjarc.ktor_template.core.types

import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.urosjarc.ktor_template.core.Env
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

private var unit: Unit = DeterministicAeadConfig.register()
private var handle: KeysetHandle = TinkJsonProtoKeysetFormat.parseKeyset(Env.KEYSET_ENCRYPTED, InsecureSecretKeyAccess.get())
private var daead: DeterministicAead = handle.getPrimitive(
    com.google.crypto.tink.RegistryConfiguration.get(),
    DeterministicAead::class.java
)

public fun String.encrypted(): Encrypted {
    val ciphertext = daead.encryptDeterministically(this.encodeToByteArray(), byteArrayOf())
    return Encrypted(value = Base64.UrlSafe.encodeToByteArray(ciphertext))
}

@JvmInline
@Serializable
public value class Encrypted(
    @Contextual public val value: ByteArray,
) {
    override fun toString(): String = this.value.decodeToString()

    public fun decrypt(): String {
        val ciphertext = Base64.UrlSafe.decode(this.value)
        return daead.decryptDeterministically(ciphertext, byteArrayOf()).decodeToString()
    }
}
