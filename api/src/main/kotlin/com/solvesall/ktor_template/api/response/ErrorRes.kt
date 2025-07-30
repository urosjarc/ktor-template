package com.solvesall.ktor_template.api.response

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
