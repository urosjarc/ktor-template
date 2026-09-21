package com.urosjarc.ktor_template.api.plugins

import com.urosjarc.ktor_template.app.App
import com.urosjarc.ktor_template.app.IdempotencyContext
import com.urosjarc.ktor_template.app.IdempotencyContextElement
import com.urosjarc.ktor_template.core.usecases.UseCase
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

val IdempotencyPlugin = createApplicationPlugin(
    name = "IdempotencyPlugin",
) {
    application.intercept(ApplicationCallPipeline.Plugins) {

        val key = call.request
            .headers["Idempotency-Key"]
            ?.let { Uuid.parse(it) }
            ?: throw BadRequestException(
                "Missing or invalid Idempotency-Key"
            )

        val idempotencyContext = IdempotencyContext(key = key, scope = null, executionId = null)

        withContext(
            UseCase.Element(executor = App.services.idempotency)
                    + IdempotencyContextElement(value = idempotencyContext)
        ) {
            proceed()
        }
    }
}
