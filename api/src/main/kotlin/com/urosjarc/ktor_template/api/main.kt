package com.urosjarc.ktor_template.api

import com.urosjarc.ktor_template.api.extend.ext_module
import com.urosjarc.ktor_template.app.App
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

suspend fun main() {
    App.init(inMemoryDb = true, createDropDb = true)
    embeddedServer(
        Netty,
        host = "0.0.0.0",
        port = Env.API_PORT,
        module = Application::ext_module,
    ).start(wait = true)
}
