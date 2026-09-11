package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Encrypted
import com.urosjarc.ktor_template.core.types.Hashed
import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlinx.serialization.Serializable

@Serializable
public data class User(
    val id: Id<User> = Id(),
    val username: String,
    val password: Hashed,
    val email: Encrypted,
    val phone: Encrypted,
    val address: Encrypted,
    val type: Type,
    val createdAt: TimeZoned = TimeZoned.nowUtc(),
    val updatedAt: TimeZoned = TimeZoned.nowUtc(),
) {

    public enum class Type {
        ADMIN,
        CUSTOMER
    }
}
