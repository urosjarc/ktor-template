package com.solvesall.ktor_template.api.extend

import com.solvesall.ktor_template.api.response.ErrorRes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall

suspend fun ApplicationCall.ext_clientError(info: String) {
    val status = HttpStatusCode.BadRequest
    return this.respond(
        status = HttpStatusCode.BadRequest,
        message =
            ErrorRes(
                error = ErrorRes.Type.USER,
                status = status.description,
                info = info,
            ),
    )
}

suspend fun ApplicationCall.ext_invalidTimeZonedParameter(data: String) {
    val status = HttpStatusCode.BadRequest
    return this.respond(
        status = HttpStatusCode.BadRequest,
        message =
            ErrorRes(
                error = ErrorRes.Type.USER,
                status = status.description,
                info = "User provided invalid (url-safe) ISO-8601 parameter '$data', try again with formats: '${URLEncoder.encode(TimeZoned.nowSlovenia().toString())}' or '${URLEncoder.encode(TimeZoned.nowUtc().toString())}'",
            ),
    )
}

suspend fun ApplicationCall.ext_invalidTimeZoned(data: String) {
    val status = HttpStatusCode.BadRequest
    return this.respond(
        status = HttpStatusCode.BadRequest,
        message =
            ErrorRes(
                error = ErrorRes.Type.USER,
                status = status.description,
                info = "User provided invalid ISO-8601 parameter '$data', try again with formats: '${TimeZoned.nowSlovenia()}' or '${TimeZoned.nowUtc()}'",
            ),
    )
}

suspend fun ApplicationCall.ext_invalidEnum(data: String) {
    val status = HttpStatusCode.BadRequest
    return this.respond(
        status = HttpStatusCode.BadRequest,
        message =
            ErrorRes(
                error = ErrorRes.Type.USER,
                status = status.description,
                info = "User provided invalid enumerator string: '$data'",
            ),
    )
}
suspend fun ApplicationCall.ext_invalidEnums(data: String, allEnums: Iterable<String>) {
    val status = HttpStatusCode.BadRequest
    return this.respond(
        status = HttpStatusCode.BadRequest,
        message =
            ErrorRes(
                error = ErrorRes.Type.USER,
                status = status.description,
                info = "User chose enumerator '$data' which does not exist inside: $allEnums",
            ),
    )
}

suspend fun ApplicationCall.ext_clientUnauthorized() {
    val status = HttpStatusCode.Unauthorized
    return this.respond(
        status = status,
        message =
            ErrorRes(
                error = ErrorRes.Type.USER,
                status = status.description,
                info = "Client unauthorized!",
            ),
    )
}

suspend fun ApplicationCall.ext_clientForbiddenAccess() {
    val status = HttpStatusCode.Forbidden
    return this.respond(
        status = status,
        message =
            ErrorRes(
                error = ErrorRes.Type.USER,
                status = status.description,
                info = "Forbidden access!",
            ),
    )
}

suspend fun ApplicationCall.ext_systemError(info: String) {
    val status = HttpStatusCode.InternalServerError
    return this.respond(
        status = HttpStatusCode.InternalServerError,
        message =
            ErrorRes(
                error = ErrorRes.Type.SYSTEM,
                status = status.description,
                info = info,
            ),
    )
}
