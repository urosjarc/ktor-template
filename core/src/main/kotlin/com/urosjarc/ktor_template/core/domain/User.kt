package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Encrypted
import com.urosjarc.ktor_template.core.types.Events
import com.urosjarc.ktor_template.core.types.Hashed
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.serialization.Serializable

@Serializable
public data class User(
    override val id: Id<User> = Id(),
    val username: String,
    val password: Hashed,
    val email: Encrypted,
    val type: Type,
    override val events: Events = Events()
): Domain<User> {

    public enum class Type {
        ADMIN,
        CUSTOMER,
        GUEST,
    }
}
