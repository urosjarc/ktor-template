package com.urosjarc.ktor_template.api.extend

import com.urosjarc.ktor_template.api.plugins.SecurityConfig
import com.urosjarc.ktor_template.api.plugins.SecurityIdentity
import com.urosjarc.ktor_template.app.App
import com.urosjarc.ktor_template.core.domain.User

fun SecurityConfig.ext_configure() {
    jwt {
        enabled = true
        testRole = "ADMIN"
        audience = "api"

        authenticate { _, token ->
            App.services.jwt
                .decode(jwt = token, audience = audience)
                ?.let {
                    val user = App.services.json.decode<User>(it.payload)
                    SecurityIdentity(role = user.type.name)
                }
        }
    }

    role(
        "ADMIN",
        "*:*",
        "!DELETE:/entities/users/**",
    )

    role(
        "USER",
        "GET:/entities/**",
        "POST:/entities/search",
    )

    pathValues {
        values(
            "name",
            "users",
            "tasks",
            "orders",
        )
    }
}
