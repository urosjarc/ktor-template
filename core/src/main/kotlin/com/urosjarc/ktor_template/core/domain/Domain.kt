package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlinx.serialization.Serializable

@Serializable
public open class Domain<T>(
    public val id: Id<T> = Id(),
    public val createdAt: TimeZoned = TimeZoned.nowUtc(),
    public val updatedAt: TimeZoned = TimeZoned.nowUtc(),
)
