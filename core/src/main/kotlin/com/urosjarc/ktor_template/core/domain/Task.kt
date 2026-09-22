package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Events
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.serialization.Serializable

@Serializable
public data class Task(
    override val id: Id<Task> = Id(),
    val userId: Id<User>,
    val name: String,
    override val events: Events = Events()
): Domain<Task>
