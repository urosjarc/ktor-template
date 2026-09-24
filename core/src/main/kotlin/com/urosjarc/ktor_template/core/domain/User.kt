package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.annotations.Put
import com.urosjarc.ktor_template.core.domain.generated.UserPut
import com.urosjarc.ktor_template.core.types.Encrypted
import com.urosjarc.ktor_template.core.types.Events
import com.urosjarc.ktor_template.core.types.Hashed
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.serialization.Serializable

@Serializable
public data class User(
    override val id: Id<User> = Id(),
    @Put
    val username: String,
    @Put
    val password: Hashed,
    @Put
    val email: Encrypted,
    @Put
    val type: Type,
    override val events: Events = Events()
): Domain<User> {

    public enum class Type {
        ADMIN,
        CUSTOMER,
        GUEST,
    }
}
