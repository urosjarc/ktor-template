package com.solvesall.ktor_template.core.domain

import com.solvesall.ktor_template.core.types.Encrypted
import com.solvesall.ktor_template.core.types.Hashed
import com.solvesall.ktor_template.core.types.Id
import com.solvesall.ktor_template.core.types.TimeZoned

public data class User(
    val id: Id<User> = Id(),
    val username: String,
    val password: Hashed,
    val email: Encrypted,
    val phone: Encrypted,
    val address: Encrypted,
    val createdAt: TimeZoned = TimeZoned.nowUtc(),
    val updatedAt: TimeZoned = TimeZoned.nowUtc(),
)
