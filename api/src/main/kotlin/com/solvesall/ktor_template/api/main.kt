package com.solvesall.ktor_template.api

import com.solvesall.ktor_template.app.App
import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.extend.random

suspend fun main() {
    App.init(inMemoryDb = false)
    val user = random<User>()
    App.services.db.transaction("CREATE") {
        it.repos.user.insert(obj = user)
    }
    println("created user: $user")
}
