package com.urosjarc.ktor_template.api.extend

import com.urosjarc.ktor_template.api.Env
import com.urosjarc.ktor_template.api.routes.v1.entitiesRoutes
import com.urosjarc.ktor_template.api.routes.v1.systemRoutes
import com.urosjarc.ktor_template.app.App
import com.urosjarc.ktor_template.core.extend.ext_resourceFile
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.logging.Log4j2Metrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.apache.logging.log4j.kotlin.logger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun Application.ext_module() {
    install(CORS) {
        this.allowHeader(HttpHeaders.ContentType)
        this.allowHeader(HttpHeaders.Authorization)
        this.anyHost()
        this.allowMethod(HttpMethod.Post)
        this.allowMethod(HttpMethod.Put)
        this.allowMethod(HttpMethod.Get)
        this.allowMethod(HttpMethod.Delete)
    }

    install(Resources)

    install(ContentNegotiation) {
        json(App.services.json.module)
    }

    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            logger.warn("CLIENT ERROR", cause)
            call.ext_clientError(info = cause::class.simpleName.toString())
        }
        exception<Throwable> { call, cause ->
            logger.error("SYSTEM ERROR", cause)
            call.ext_systemError(info = cause::class.simpleName.toString())
        }
    }

    install(MicrometerMetrics) {
        registry = Env.appMicrometerRegistry

        meterBinders = listOf(
            UptimeMetrics(),
            Log4j2Metrics(),
            JvmCompilationMetrics(),
            JvmHeapPressureMetrics(),
            JvmInfoMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics()
        )

        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .maximumExpectedValue(20.seconds.inWholeNanoseconds.toDouble())
            .serviceLevelObjectives(
                100.milliseconds.inWholeNanoseconds.toDouble(),
                500.milliseconds.inWholeNanoseconds.toDouble()
            )
            .build()
    }

    install(CallLogging) {
        format { call ->
            val status = call.response.status()!!
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val host = call.request.host()
            val parameters = Json.encodeToJsonElement(call.request.queryParameters.entries())

            "Status: $status, HTTP method: $httpMethod"
        }
    }

    install(SSE)

    install(Authentication) {
        provider("custom-token") {
            authenticate { context ->
                val authHeader = context.call.request.headers[HttpHeaders.Authorization]
                val token = authHeader?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()

                if (token != null) {
                    val jwt = App.services.jwt.decode(jwt = token, audience = "api")
                    if (jwt != null) {
                        return@authenticate
                    }
                }

                context.challenge("custom-token", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                    call.ext_clientUnauthorized()
                    challenge.complete()
                }
            }
        }
    }

    routing {
        route("api") {

            swaggerUI(path = "docs", apiFile = "openapi/documentation.yaml".ext_resourceFile())
            systemRoutes()

            authenticate("custom-token") {
                route("v1") {
                    entitiesRoutes()
                }
            }
        }
    }
}
