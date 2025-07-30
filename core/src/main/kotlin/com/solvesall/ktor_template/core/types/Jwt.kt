package com.solvesall.ktor_template.core.types

import kotlin.time.Instant

public data class Jwt(
    val audiences: List<String>,
    val expiration: Instant,
    val payload: String,
)
