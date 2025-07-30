package com.solvesall.ktor_template.api.routes.v1

import com.solvesall.ktor_template.api.Env
import com.solvesall.ktor_template.api.response.SystemStatus
import com.solvesall.ktor_template.core.types.TimeZoned
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.resources.get
import io.ktor.server.response.respond

/**
 * @OpenAPITag System
 * Providing system endpoints
 */

@Resource("system")
class system {
    @Resource("status")
    class status(val parent: system)

    @Resource("metrics")
    class metrics(val parent: system)
}

fun Route.systemRoutes() {
    /**
     * Get system status report.
     */
    get<system.status> {
        val systemStatus = SystemStatus(
            systemTime = SystemStatus.Time(
                system = TimeZoned.nowSystem(),
                utc = TimeZoned.nowUtc(),
                slovenia = TimeZoned.nowSlovenia()
            ),
            dbService = SystemStatus.Status.OK,
        )
        this.call.respond(systemStatus)
    }

    /**
     * Get system metrics
     */
    get<system.metrics> {
        call.respondText(ContentType.Text.Plain, HttpStatusCode.OK) {
            Env.appMicrometerRegistry.scrape()
        }
    }
}
