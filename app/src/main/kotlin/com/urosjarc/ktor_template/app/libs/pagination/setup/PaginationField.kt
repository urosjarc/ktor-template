package com.urosjarc.ktor_template.app.libs.pagination.setup

import org.jetbrains.exposed.v1.core.ExpressionWithColumnType

public data class PaginationField<T>(
    public val expression: ExpressionWithColumnType<T>,
    public val paginationType: PaginationType? = null,
    public val filterPlacement: Place = Place.WHERE,
) {

    public enum class Place { WHERE, HAVING }

    public val resolvedPaginationType: PaginationType
        get() = paginationType ?: PaginationType.from(expression.columnType)
}
