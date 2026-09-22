package com.urosjarc.ktor_template.core.types

import kotlinx.serialization.Serializable

@Serializable
public data class Events(
    public val createdAt: TimeZoned = TimeZoned.nowUtc(),
    public val insertedAt: TimeZoned? = null,
    public val updatedAt: TimeZoned? = null,
)
