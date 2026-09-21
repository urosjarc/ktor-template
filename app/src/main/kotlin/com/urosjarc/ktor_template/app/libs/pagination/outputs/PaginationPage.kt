package com.urosjarc.ktor_template.app.libs.pagination.outputs

import kotlinx.serialization.Serializable

@Serializable
public data class PaginationPage<T>(
    val data: List<T>,
    val nextCursor: String?,
)
