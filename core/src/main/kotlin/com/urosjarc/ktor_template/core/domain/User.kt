package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Encrypted
import com.urosjarc.ktor_template.core.types.Hashed
import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlinx.serialization.Serializable

@Serializable
public data class User(
    val username: String,
    val password: Hashed,
    val email: Encrypted,
    val type: Type,
    override val id: Id<User>,
    override val createdAt: TimeZoned,
    override val updatedAt: TimeZoned,
) : Domain<User> {

    public enum class Type {
        ADMIN,
        CUSTOMER
    }
}
