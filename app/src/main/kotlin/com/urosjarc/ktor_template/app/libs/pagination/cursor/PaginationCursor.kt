package com.urosjarc.ktor_template.app.libs.pagination.cursor

import com.urosjarc.ktor_template.core.types.Pagination
import kotlinx.serialization.Serializable

@Serializable
public data class PaginationCursor(
    public val queryHash: String,
    public val sort: List<Pagination.Sort>,
    public val values: List<String?>,
)
