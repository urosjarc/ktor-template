package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlinx.serialization.Serializable

@Serializable
public data class Task(
    val id: Id<Task> = Id(),
    val userId: Id<User>,
    val name: String,
    val createdAt: TimeZoned = TimeZoned.nowUtc(),
    val updatedAt: TimeZoned = TimeZoned.nowUtc(),
)
