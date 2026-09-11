package com.urosjarc.ktor_template.api.responses

import kotlinx.serialization.Serializable

@Serializable
data class ErrorRes(
    val error: Type,
    val status: String,
    val info: String,
) {
    enum class Type {
        USER,
        SYSTEM,
    }
}
