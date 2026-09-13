package com.urosjarc.ktor_template.core.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class Pagination(
    val filter: Filter? = null,
    val sort: List<Sort> = emptyList(),
    val cursor: String? = null,
    val limit: Int = 50
) {
    public data class Page<T>(
        val data: List<T>,
        val nextCursor: String?,
    )

    @Serializable
    public data class Sort(
        val column: String,
        val direction: Direction
    ) {
        @Serializable
        public enum class Direction { ASC, DESC }
    }

    @Serializable
    public sealed interface Filter {

        @Serializable
        @SerialName("condition")
        public data class Condition(
            val column: String,
            val operator: Operator,
            val value: JsonElement? = null
        ) : Filter

        @Serializable
        public enum class Operator {
            EQ, NE,
            GT, GTE,
            LT, LTE,
            BETWEEN, CONTAINS, STARTS_WITH, ENDS_WITH,
            IN, NOT_IN,
            IS_NULL, IS_NOT_NULL
        }

        @Serializable
        @SerialName("and")
        public data class And(val filters: List<Filter>) : Filter

        @Serializable
        @SerialName("or")
        public data class Or(val filters: List<Filter>) : Filter
    }
}
