package com.urosjarc.ktor_template.core.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request sent by the client.
 *
 * The frontend may keep filter/sort state in the URL, while [cursor] remains
 * transient API state in the POST body.
 */
@Serializable
public data class Pagination(
    val limit: Int = 20,
    val sort: List<Sort> = emptyList(),
    val filter: Filter? = null,
    val cursor: String? = null,
) {
    @Serializable
    public data class Sort(
        val column: String,
        val direction: Direction = Direction.ASC,
    ) {
        @Serializable
        public enum class Direction {
            ASC, DESC,
        }
    }

    @Serializable
    public sealed interface Filter {

        @Serializable
        @SerialName("Condition")
        public data class Condition(
            val column: String,
            val operator: Operator,
            val value: JsonElement? = null,
        ) : Filter

        @Serializable
        @SerialName("And")
        public data class And(
            var filters: List<Filter>,
        ) : Filter {
            public constructor(vararg filters: Filter) : this(filters.toList())
        }

        @Serializable
        @SerialName("Or")
        public data class Or(
            val filters: List<Filter>,
        ) : Filter {
            public constructor(vararg filters: Filter) : this(filters.toList())
        }

        @Serializable
        public enum class Operator {
            EQ, NE, GT, GTE, LT, LTE, BETWEEN, CONTAINS, STARTS_WITH, ENDS_WITH, IN, NOT_IN, IS_NULL, IS_NOT_NULL,
        }
    }

    @Serializable
    public data class Page<T>(
        val data: List<T>,
        val nextCursor: String?,
    )
}
