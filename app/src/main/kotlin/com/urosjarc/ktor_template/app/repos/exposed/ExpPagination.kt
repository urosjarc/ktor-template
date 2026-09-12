package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.core.types.Pagination
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.Query
import java.util.*

/**
 * Query metadata for an Exposed table.
 *
 * Every physical table column is queryable. No manual column map is needed;
 * Exposed's Table.columns is used directly.
 */
public class QueryTable(
    public val table: Table,
    public val primaryKey: String,
    public val defaultSort: List<Pagination.Sort> = listOf(
        Pagination.Sort(primaryKey, Pagination.Sort.Direction.ASC)
    )
) {

    public fun column(
        name: String
    ): Column<*> =
        table.columns.firstOrNull { it.name == name }
            ?: error("Unknown column: $name")
}


// ============================================================================
// PAGINATION COMPILER
// ============================================================================

public class PaginationCompiler(
    private val table: QueryTable
) {

    private val json = Json

    /**
     * Applies:
     *
     * - recursive filtering
     * - cursor predicate
     * - multi-column sorting
     * - deterministic primary-key tie breaker
     * - LIMIT + 1
     * - next cursor creation
     */
    public suspend fun apply(
        request: Pagination,
        source: Query
    ): Pagination.Page<ResultRow> {

        require(request.limit > 0) {
            "limit must be greater than zero"
        }

        val sort = normalizeSort(request.sort)
        val cursor = request.cursor?.let(::decodeCursor)

        validateCursor(sort, cursor)

        val filterExpression =
            request.filter?.let(::compileFilter)

        val cursorExpression =
            cursor?.let {
                compileCursor(
                    sort = sort,
                    cursor = it
                )
            }

        val predicates = buildList {
            filterExpression?.let(::add)
            cursorExpression?.let(::add)
        }

        val rows =
            source
                .where {
                    when {
                        predicates.isEmpty() -> Op.TRUE
                        else -> predicates.compoundAnd()
                    }
                }
                .orderBy(*compileSort(sort))
                .limit(request.limit + 1)
                .toList()

        val hasNext = rows.size > request.limit

        val page =
            if (hasNext) {
                rows.take(request.limit)
            } else {
                rows
            }

        val nextCursor =
            if (hasNext) {
                page.lastOrNull()?.let { row ->
                    encodeCursor(
                        sort = sort,
                        row = row
                    )
                }
            } else {
                null
            }

        return Pagination.Page(
            data = page,
            nextCursor = nextCursor
        )
    }


    // ========================================================================
    // SORT
    // ========================================================================

    private fun normalizeSort(
        requested: List<Pagination.Sort>
    ): List<Pagination.Sort> {

        val sort =
            if (requested.isEmpty()) {
                table.defaultSort
            } else {
                requested
            }

        return if (sort.any { it.column == table.primaryKey }) {
            sort
        } else {
            sort + Pagination.Sort(
                column = table.primaryKey,
                direction = Pagination.Sort.Direction.ASC
            )
        }
    }


    private fun compileSort(
        sort: List<Pagination.Sort>
    ): Array<Pair<Column<*>, SortOrder>> =
        sort.map { item ->
            table.column(item.column) to when (item.direction) {
                Pagination.Sort.Direction.ASC -> SortOrder.ASC
                Pagination.Sort.Direction.DESC -> SortOrder.DESC
            }
        }.toTypedArray()


    // ========================================================================
    // FILTER
    // ========================================================================

    private fun compileFilter(
        filter: Pagination.Filter
    ): Op<Boolean> =
        when (filter) {
            is Pagination.Filter.Condition ->
                compileCondition(filter)

            is Pagination.Filter.And -> {
                require(filter.filters.isNotEmpty()) {
                    "AND requires at least one filter"
                }

                filter.filters
                    .map(::compileFilter)
                    .compoundAnd()
            }

            is Pagination.Filter.Or -> {
                require(filter.filters.isNotEmpty()) {
                    "OR requires at least one filter"
                }

                filter.filters
                    .map(::compileFilter)
                    .compoundOr()
            }
        }


    private fun compileCondition(
        condition: Pagination.Filter.Condition
    ): Op<Boolean> {

        val column = table.column(condition.column)

        return when (condition.operator) {
            Pagination.Filter.Operator.EQ ->
                comparison(
                    column = column,
                    operator = "=",
                    value = requiredValue(column, condition)
                )

            Pagination.Filter.Operator.NE ->
                comparison(
                    column = column,
                    operator = "<>",
                    value = requiredValue(column, condition)
                )

            Pagination.Filter.Operator.GT ->
                comparison(
                    column = column,
                    operator = ">",
                    value = requiredValue(column, condition)
                )

            Pagination.Filter.Operator.GTE ->
                comparison(
                    column = column,
                    operator = ">=",
                    value = requiredValue(column, condition)
                )

            Pagination.Filter.Operator.LT ->
                comparison(
                    column = column,
                    operator = "<",
                    value = requiredValue(column, condition)
                )

            Pagination.Filter.Operator.LTE ->
                comparison(
                    column = column,
                    operator = "<=",
                    value = requiredValue(column, condition)
                )

            Pagination.Filter.Operator.BETWEEN ->
                between(
                    column = column,
                    value = condition.value
                )

            Pagination.Filter.Operator.CONTAINS ->
                like(
                    column = column,
                    value = "%${requiredPrimitive(condition).content}%"
                )

            Pagination.Filter.Operator.STARTS_WITH ->
                like(
                    column = column,
                    value = "${requiredPrimitive(condition).content}%"
                )

            Pagination.Filter.Operator.ENDS_WITH ->
                like(
                    column = column,
                    value = "%${requiredPrimitive(condition).content}"
                )

            Pagination.Filter.Operator.IN ->
                inList(
                    column = column,
                    value = condition.value,
                    negate = false
                )

            Pagination.Filter.Operator.NOT_IN ->
                inList(
                    column = column,
                    value = condition.value,
                    negate = true
                )

            Pagination.Filter.Operator.IS_NULL ->
                nullComparison(
                    column = column,
                    isNull = true
                )

            Pagination.Filter.Operator.IS_NOT_NULL ->
                nullComparison(
                    column = column,
                    isNull = false
                )
        }
    }


    private fun requiredValue(
        column: Column<*>,
        condition: Pagination.Filter.Condition
    ): Any =
        condition.value?.let {
            parseValue(column, it)
        } ?: error(
            "Operator ${condition.operator} requires a value"
        )


    private fun requiredPrimitive(
        condition: Pagination.Filter.Condition
    ): JsonPrimitive =
        condition.value?.jsonPrimitive ?: error(
            "Operator ${condition.operator} requires a value"
        )


    private fun between(
        column: Column<*>,
        value: JsonElement?
    ): Op<Boolean> {

        val array =
            value as? JsonArray ?: error(
                "BETWEEN requires [from, to]"
            )

        require(array.size == 2) {
            "BETWEEN requires exactly two values"
        }

        val from = parseValue(column, array[0])
        val to = parseValue(column, array[1])

        return object : Op<Boolean>() {

            override fun toQueryBuilder(
                queryBuilder: QueryBuilder
            ) {
                queryBuilder.append(column)
                queryBuilder.append(" BETWEEN ")

                registerArgument(
                    queryBuilder = queryBuilder,
                    column = column,
                    value = from
                )

                queryBuilder.append(" AND ")

                registerArgument(
                    queryBuilder = queryBuilder,
                    column = column,
                    value = to
                )
            }
        }
    }


    private fun inList(
        column: Column<*>,
        value: JsonElement?,
        negate: Boolean
    ): Op<Boolean> {

        val array =
            value as? JsonArray ?: error(
                "${if (negate) Pagination.Filter.Operator.NOT_IN else Pagination.Filter.Operator.IN} " +
                        "requires an array"
            )

        require(array.isNotEmpty()) {
            "IN / NOT_IN requires at least one value"
        }

        val values = array.map {
            parseValue(column, it)
        }

        return object : Op<Boolean>() {

            override fun toQueryBuilder(
                queryBuilder: QueryBuilder
            ) {

                queryBuilder.append(column)
                queryBuilder.append(
                    if (negate) {
                        " NOT IN ("
                    } else {
                        " IN ("
                    }
                )

                values.forEachIndexed { index, item ->
                    if (index > 0) {
                        queryBuilder.append(", ")
                    }

                    registerArgument(
                        queryBuilder = queryBuilder,
                        column = column,
                        value = item
                    )
                }

                queryBuilder.append(")")
            }
        }
    }


    private fun nullComparison(
        column: Column<*>,
        isNull: Boolean
    ): Op<Boolean> =
        object : Op<Boolean>() {

            override fun toQueryBuilder(
                queryBuilder: QueryBuilder
            ) {
                queryBuilder.append(column)
                queryBuilder.append(
                    if (isNull) {
                        " IS NULL"
                    } else {
                        " IS NOT NULL"
                    }
                )
            }
        }


    @Suppress("UNCHECKED_CAST")
    private fun comparison(
        column: Column<*>,
        operator: String,
        value: Any
    ): Op<Boolean> =
        object : Op<Boolean>() {

            override fun toQueryBuilder(
                queryBuilder: QueryBuilder
            ) {
                queryBuilder.append(column)
                queryBuilder.append(" $operator ")

                registerArgument(
                    queryBuilder = queryBuilder,
                    column = column,
                    value = value
                )
            }
        }


    private fun like(
        column: Column<*>,
        value: String
    ): Op<Boolean> =
        comparison(
            column = column,
            operator = "LIKE",
            value = value
        )


    @Suppress("UNCHECKED_CAST")
    private fun registerArgument(
        queryBuilder: QueryBuilder,
        column: Column<*>,
        value: Any?
    ) {
        queryBuilder.registerArgument(
            column.columnType as IColumnType<Any>,
            value
        )
    }


    // ========================================================================
    // CURSOR
    // ========================================================================

    private data class Cursor(
        val sort: List<Pagination.Sort>,
        val values: List<String?>
    )


    @Serializable
    private data class CursorPayload(
        val sort: List<Pagination.Sort>,
        val values: List<String?>
    )


    private fun encodeCursor(
        sort: List<Pagination.Sort>,
        row: ResultRow
    ): String {

        val values = sort.map { item ->
            val value = row[table.column(item.column)]

            require(value != null) {
                "Cannot create a cursor from a NULL sort value " +
                        "for column ${item.column}"
            }

            value.toString()
        }

        val payload =
            CursorPayload(
                sort = sort,
                values = values
            )

        val jsonValue =
            json.encodeToString(payload)

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                jsonValue.toByteArray(Charsets.UTF_8)
            )
    }


    private fun decodeCursor(
        value: String
    ): Cursor {

        val decoded = try {
            String(
                Base64
                    .getUrlDecoder()
                    .decode(value),
                Charsets.UTF_8
            )
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid pagination cursor",
                cause
            )
        }

        val payload = try {
            json.decodeFromString<CursorPayload>(decoded)
        } catch (cause: Exception) {
            throw IllegalArgumentException(
                "Invalid pagination cursor",
                cause
            )
        }

        return Cursor(
            sort = payload.sort,
            values = payload.values
        )
    }


    private fun validateCursor(
        sort: List<Pagination.Sort>,
        cursor: Cursor?
    ) {

        if (cursor == null) {
            return
        }

        require(cursor.sort == sort) {
            "Cursor was created for a different sort order"
        }

        require(cursor.values.size == sort.size) {
            "Cursor contains ${cursor.values.size} values " +
                    "but sort contains ${sort.size} columns"
        }
    }


    /**
     * Generates the lexicographic keyset predicate.
     *
     * Example:
     *
     *     ORDER BY age ASC, name DESC, id ASC
     *
     * becomes:
     *
     *     age > :age
     *     OR (
     *         age = :age
     *         AND name < :name
     *     )
     *     OR (
     *         age = :age
     *         AND name = :name
     *         AND id > :id
     *     )
     */
    private fun compileCursor(
        sort: List<Pagination.Sort>,
        cursor: Cursor
    ): Op<Boolean> {

        val branches = sort.indices.map { index ->

            val equalities =
                sort.indices
                    .take(index)
                    .map { previousIndex ->

                        val previousSort =
                            sort[previousIndex]

                        val previousValue =
                            cursor.values[previousIndex]
                                ?: error(
                                    "NULL cursor values are not " +
                                            "supported for sorting"
                                )

                        val previousColumn =
                            table.column(previousSort.column)

                        comparison(
                            column = previousColumn,
                            operator = "=",
                            value = parseCursorValue(
                                previousColumn,
                                previousValue
                            )
                        )
                    }

            val currentSort =
                sort[index]

            val currentColumn =
                table.column(currentSort.column)

            val currentValue =
                cursor.values[index]
                    ?: error(
                        "NULL cursor values are not " +
                                "supported for sorting"
                    )

            val operator =
                when (currentSort.direction) {
                    Pagination.Sort.Direction.ASC -> ">"
                    Pagination.Sort.Direction.DESC -> "<"
                }

            val currentComparison =
                comparison(
                    column = currentColumn,
                    operator = operator,
                    value = parseCursorValue(
                        currentColumn,
                        currentValue
                    )
                )

            (equalities + currentComparison)
                .compoundAnd()
        }

        return branches.compoundOr()
    }


    // ========================================================================
    // VALUE PARSING
    // ========================================================================

    /**
     * Convert the JSON value to the Kotlin value expected by the Exposed
     * column type.
     *
     * Supported generic query types:
     * - String
     * - numeric types
     * - UUID (useful for UUIDv7 primary keys)
     *
     * Other column types are deliberately rejected because this query system
     * is intentionally limited to strings and numbers.
     */
    private fun parseValue(
        column: Column<*>,
        value: JsonElement
    ): Any {
        return parseStringValue(
            column = column,
            value = value.jsonPrimitive.content
        )
    }


    private fun parseCursorValue(
        column: Column<*>,
        value: String
    ): Any {
        return parseStringValue(
            column = column,
            value = value
        )
    }


    private fun parseStringValue(
        column: Column<*>,
        value: String
    ): Any {

        val sqlType =
            column.columnType
                .sqlType()
                .uppercase()
                .trim()

        return when {
            sqlType.startsWith("VARCHAR") ||
                    sqlType.startsWith("CHAR") ||
                    sqlType.startsWith("TEXT") ||
                    sqlType.startsWith("CLOB") ->
                value

            sqlType.startsWith("TINYINT") ||
                    sqlType.startsWith("BYTEINT") ->
                value.toByte()

            sqlType.startsWith("SMALLINT") ->
                value.toShort()

            sqlType.startsWith("INTEGER") ||
                    sqlType.startsWith("INT") ->
                value.toInt()

            sqlType.startsWith("BIGINT") ->
                value.toLong()

            sqlType.startsWith("REAL") ->
                value.toFloat()

            sqlType.startsWith("DOUBLE") ||
                    sqlType.startsWith("FLOAT") ->
                value.toDouble()

            sqlType.startsWith("DECIMAL") ||
                    sqlType.startsWith("NUMERIC") ->
                value.toBigDecimal()

            sqlType.startsWith("UUID") ->
                UUID.fromString(value)

            else ->
                error(
                    "Unsupported query column type " +
                            "${column.columnType.sqlType()} " +
                            "for column ${column.name}"
                )
        }
    }
}
