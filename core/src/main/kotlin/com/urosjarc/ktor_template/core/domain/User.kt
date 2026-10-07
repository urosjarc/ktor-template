package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.annotations.Persistence
import com.urosjarc.ktor_template.core.annotations.Post
import com.urosjarc.ktor_template.core.annotations.Put
import com.urosjarc.ktor_template.core.types.Encrypted
import com.urosjarc.ktor_template.core.types.Events
import com.urosjarc.ktor_template.core.types.Hashed
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.serialization.Serializable

@Persistence(schema = "public", table = "users")
@Serializable
public data class User(
    override val id: Id<User> = Id(),
    @Put @Post val username: String,
    @Put @Post val password: Hashed,
    @Put @Post val email: Encrypted,
    @Put @Post val type: Type,

    override val events: Events = Events()
): Domain<User> {

    public enum class Type {
        ADMIN,
        CUSTOMER,
        GUEST,
    }
}
