package com.urosjarc.ktor_template.core.services

import com.urosjarc.ktor_template.core.types.Jwt
import kotlin.time.Duration

public interface JwtService {
    public val publicKey: String
    public fun encode(payload: String, audiences: List<String>, expiration: Duration): String
    public fun decode(jwt: String, audience: String): Jwt?
}
