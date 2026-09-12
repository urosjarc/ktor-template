package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Id
import kotlinx.serialization.Serializable

@Serializable
public data class Task(
    val userId: Id<User>,
    val name: String,
): Domain<Task>()
