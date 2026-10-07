package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.annotations.Persistence
import com.urosjarc.ktor_template.core.annotations.Post
import com.urosjarc.ktor_template.core.annotations.Put
import com.urosjarc.ktor_template.core.types.Events
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.serialization.Serializable

@Serializable
@Persistence(schema = "public", table = "tasks")
public data class Task(
    override val id: Id<Task> = Id(),
    @Post @Put val userId: Id<User>,
    @Post @Put val name: String,
    override val events: Events = Events()
): Domain<Task>
