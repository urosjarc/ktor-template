package com.urosjarc.ktor_template.api.extend

import com.urosjarc.ktor_template.api.plugins.SecurityConfig

fun SecurityConfig.ext_configure() {
    role(
        "ADMIN",
        "*:*",
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
