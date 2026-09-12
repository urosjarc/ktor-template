package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Encrypted
import com.urosjarc.ktor_template.core.types.Hashed
import kotlinx.serialization.Serializable

@Serializable
public data class User(
    val username: String,
    val password: Hashed,
    val email: Encrypted,
    val type: Type,
) : Domain<User>() {

    public enum class Type {
        ADMIN,
        CUSTOMER
    }
}
