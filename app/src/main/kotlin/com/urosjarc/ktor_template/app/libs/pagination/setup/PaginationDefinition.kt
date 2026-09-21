package com.urosjarc.ktor_template.app.libs.pagination.setup

import com.urosjarc.ktor_template.core.types.Pagination
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import kotlin.collections.all
import kotlin.collections.isNotEmpty

public data class PaginationDefinition(
    public val uniqueKey: List<String> = listOf("id"),
    public val entities: List<PaginationEntity> = emptyList(),
    public val defaultSort: List<Pagination.Sort> = uniqueKey.map { Pagination.Sort(it, Pagination.Sort.Direction.ASC) },
    public val fields: Map<String, PaginationField<*>>,
) {
    init {
        require(fields.isNotEmpty()) { "Pagination definition must contain at least one field" }
        require(uniqueKey.isNotEmpty()) { "Pagination definition must contain a unique key" }
        require(uniqueKey.all(fields::containsKey)) { "All unique key fields must exist in pagination fields" }
        require(defaultSort.isNotEmpty()) { "Default sort must contain at least one field" }
        require(defaultSort.all { fields.containsKey(it.column) }) { "All default sort fields must exist in pagination fields" }
    }

    public fun columns(): List<ColumnMetadata> = fields.map { (name, field) ->
        ColumnMetadata(name, field.resolvedPaginationType)
    }

    public data class ColumnMetadata(val name: String, val type: PaginationType)

    public companion object {
        public fun <T> field(
            expression: ExpressionWithColumnType<T>,
            paginationType: PaginationType? = null,
            filterPlacement: PaginationField.Place = PaginationField.Place.WHERE,
        ): PaginationField<T> = PaginationField(expression, paginationType, filterPlacement)
    }
}
