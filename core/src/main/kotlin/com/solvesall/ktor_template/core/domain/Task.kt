package com.solvesall.ktor_template.core.domain

import com.solvesall.ktor_template.core.types.Id
import com.solvesall.ktor_template.core.types.TimeZoned

public data class Task(
    val id: Id<Task> = Id(),
    val userId: Id<User>,
    val name: String,
    val createdAt: TimeZoned = TimeZoned.nowUtc(),
    val updatedAt: TimeZoned = TimeZoned.nowUtc(),
): IdEntity<Task>()
