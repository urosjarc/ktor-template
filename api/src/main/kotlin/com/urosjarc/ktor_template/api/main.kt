package com.urosjarc.ktor_template.api

import com.urosjarc.ktor_template.api.extend.ext_module
import com.urosjarc.ktor_template.app.App
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.extend.random
import com.urosjarc.ktor_template.core.types.Id
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

suspend fun main() {
    App.init(inMemoryDb = true, createDropDb = true)
    App.seed()

    println(App.services.idempotency.treeReport(
        rootId = Id(uuid = Uuid.parse("01a0c5c4-7723-762f-b541-08a814d138f3")),
    ))

    println(App.services.jwt.encode(
        payload = App.services.json.encode(random<User>().copy(type=User.Type.ADMIN)),
        audiences = listOf("api"),
        expiration = 1.hours
    ))
    embeddedServer(
        Netty,
        host = "0.0.0.0",
        port = Env.API_PORT,
        module = Application::ext_module,
    ).start(wait = true)
}
