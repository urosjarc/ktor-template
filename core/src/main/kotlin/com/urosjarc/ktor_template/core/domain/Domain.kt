package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlinx.serialization.Serializable

@Serializable
public sealed interface Domain<T> {
    public val id: Id<T>
    public val createdAt: TimeZoned
    public val updatedAt: TimeZoned
}
