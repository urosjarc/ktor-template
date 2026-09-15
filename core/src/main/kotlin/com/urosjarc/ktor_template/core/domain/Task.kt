package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlinx.serialization.Serializable

@Serializable
public data class Task(
    val userId: Id<User>,
    val name: String,
    override val id: Id<Task>,
    override val createdAt: TimeZoned,
    override val updatedAt: TimeZoned,
) : Domain<Task>
