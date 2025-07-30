package com.solvesall.ktor_template.app.services

import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkJsonProtoKeysetFormat
import com.google.crypto.tink.jwt.*
import com.solvesall.ktor_template.core.services.JwtService
import com.solvesall.ktor_template.core.types.Jwt
import org.apache.logging.log4j.kotlin.logger
import java.security.GeneralSecurityException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant


public class TinkeyJwtService(privateKeyset: String) : JwtService {
    public override val publicKey: String
    private val signer: JwtPublicKeySign
    private var verifier: JwtPublicKeyVerify

    init {
        JwtSignatureConfig.register()

        val privateKeysetHandle = TinkJsonProtoKeysetFormat.parseKeyset(privateKeyset, InsecureSecretKeyAccess.get())
        this.signer = privateKeysetHandle.getPrimitive(RegistryConfiguration.get(), JwtPublicKeySign::class.java)

        this.publicKey = JwkSetConverter.fromPublicKeysetHandle(privateKeysetHandle.getPublicKeysetHandle())
        val publicKeysetHandle = JwkSetConverter.toPublicKeysetHandle(this.publicKey)
        this.verifier = publicKeysetHandle.getPrimitive(RegistryConfiguration.get(), JwtPublicKeyVerify::class.java)
    }

    override fun encode(payload: String, audiences: List<String>, expiration: Duration): String {
        val rawJwt = RawJwt.newBuilder()
            .setAudiences(audiences)
            .addStringClaim("payload", payload)
            .setExpiration(Clock.System.now().plus(expiration).toJavaInstant())
            .build()
        return this.signer.signAndEncode(rawJwt)
    }

    override fun decode(jwt: String, audience: String): Jwt? {
        val validator = JwtValidator.newBuilder().expectAudience(audience).build()
        try {
            val verifiedJwt = verifier.verifyAndDecode(jwt, validator)
            return Jwt(
                audiences = verifiedJwt.audiences,
                expiration = verifiedJwt.expiration.toKotlinInstant(),
                payload = verifiedJwt.getStringClaim("payload")
            )
        } catch (e: GeneralSecurityException) {
            logger.debug( "JWT is not valid", e)
            return null
        }
    }
}
