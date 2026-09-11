package com.urosjarc.ktor_template.api.responses

import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val systemTime: Time,
    val dbService: Status,
) {
    enum class Status { OK, FAIL }

    @Serializable
    data class Time(
        val system: TimeZoned,
        val utc: TimeZoned,
        val slovenia: TimeZoned,
    )

}
