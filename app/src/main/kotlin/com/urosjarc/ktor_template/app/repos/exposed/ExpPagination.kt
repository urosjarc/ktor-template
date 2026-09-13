package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.core.types.Pagination
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.Query
import java.util.*

private val Table.primaryKeyName: String get() {
    return this.primaryKey!!.columns.single().name
}

private fun Table.column(name: String): Column<*> {
    logger.debug("Resolving column '$name' in table '${this.tableName}'")
    return this.columns.firstOrNull { it.name == name }
        ?: run {
            logger.debug("Column '$name' not found in table '${this.tableName}'")
            error("Unknown column: $name")
        }
}

private val Table.defaultSort: List<Pagination.Sort>
    get() {
        return listOf(Pagination.Sort(this.primaryKeyName, Pagination.Sort.Direction.ASC))
    }

// ============================================================================
// PAGINATION COMPILER
// ============================================================================

public class PaginationCompiler(private val table: Table) {
    private val json = Json

    public data class Result(
        val query: Query,
        val page: Pagination.Page<ResultRow>
    )

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
    ): Result {
        logger.debug("Applying pagination: limit=${request.limit}, sort=${request.sort}, cursor=${request.cursor}, filter=${request.filter}")

        require(request.limit > 0) {
            "limit must be greater than zero"
        }

        val sort = normalizeSort(request.sort)
        logger.debug("Normalized sort order: $sort")

        val cursor = request.cursor?.let(::decodeCursor)
        logger.debug("Decoded cursor: $cursor")

        validateCursor(sort, cursor)
        logger.debug("Cursor validation passed")

        val filterExpression =
            request.filter?.let(::compileFilter)
        logger.debug("Compiled filter expression: $filterExpression")

        val cursorExpression =
            cursor?.let {
                compileCursor(
                    sort = sort,
                    cursor = it
                )
            }
        logger.debug("Compiled cursor expression: $cursorExpression")

        val predicates = buildList {
            filterExpression?.let(::add)
            cursorExpression?.let(::add)
        }
        logger.debug("Combined predicates count: ${predicates.size}")

        logger.debug("Executing query with limit: ${request.limit + 1}")
        val query =
            source
                .where {
                    when {
                        predicates.isEmpty() -> Op.TRUE
                        else -> predicates.compoundAnd()
                    }
                }
                .orderBy(*compileSort(sort))
                .limit(request.limit + 1)

        val rows = query.toList()
        val hasNext = rows.size > request.limit
        logger.debug("Query returned ${rows.size} rows (limit=${request.limit}, hasNext=$hasNext)")

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

        logger.debug("Pagination result: page size=${page.size}, nextCursor=$nextCursor")

        return Result(
            query = query,
            page = Pagination.Page(
                data = page,
                nextCursor = nextCursor,
            )
        )
    }


    // ========================================================================
    // SORT
    // ========================================================================

    private fun normalizeSort(
        requested: List<Pagination.Sort>
    ): List<Pagination.Sort> {
        logger.debug("Normalizing sort: requested=$requested, defaultSort=${table.defaultSort}, primaryKey=${table.primaryKey}")

        val sort =
            if (requested.isEmpty()) {
                table.defaultSort
            } else {
                requested
            }

        val result = if (sort.any { it.column == table.primaryKeyName }) {
            sort
        } else {
            sort + Pagination.Sort(
                column = table.primaryKeyName,
                direction = Pagination.Sort.Direction.ASC
            )
        }
        logger.debug("Sort normalization resolved to: $result")
        return result
    }


    private fun compileSort(
        sort: List<Pagination.Sort>
    ): Array<Pair<Column<*>, SortOrder>> {
        logger.debug("Compiling sort clauses: $sort")
        val compiled = sort.map { item ->
            val col = table.column(item.column)
            val order = when (item.direction) {
                Pagination.Sort.Direction.ASC -> SortOrder.ASC
                Pagination.Sort.Direction.DESC -> SortOrder.DESC
            }
            logger.debug("Sort clause: column=${col.name}, order=$order")
            col to order
        }.toTypedArray()
        return compiled
    }


    // ========================================================================
    // FILTER
    // ========================================================================

    private fun compileFilter(
        filter: Pagination.Filter
    ): Op<Boolean> {
        logger.debug("Compiling filter: $filter")
        return when (filter) {
            is Pagination.Filter.Condition -> {
                logger.debug("Compiling filter condition: column=${filter.column}, operator=${filter.operator}, value=${filter.value}")
                compileCondition(filter)
            }

            is Pagination.Filter.And -> {
                logger.debug("Compiling AND filter with ${filter.filters.size} sub-filters")
                require(filter.filters.isNotEmpty()) {
                    "AND requires at least one filter"
                }

                filter.filters
                    .map(::compileFilter)
                    .compoundAnd()
            }

            is Pagination.Filter.Or -> {
                logger.debug("Compiling OR filter with ${filter.filters.size} sub-filters")
                require(filter.filters.isNotEmpty()) {
                    "OR requires at least one filter"
                }

                filter.filters
                    .map(::compileFilter)
                    .compoundOr()
            }
        }
    }


    private fun compileCondition(
        condition: Pagination.Filter.Condition
    ): Op<Boolean> {
        val column = table.column(condition.column)
        logger.debug("Compiling condition on column '${column.name}' with operator '${condition.operator}' and value '${condition.value}'")

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
    ): Any {
        logger.debug("Extracting required value for condition '${condition.operator}' on column '${column.name}' from '${condition.value}'")
        return condition.value?.let {
            parseValue(column, it)
        } ?: error(
            "Operator ${condition.operator} requires a value"
        )
    }


    private fun requiredPrimitive(
        condition: Pagination.Filter.Condition
    ): JsonPrimitive {
        logger.debug("Extracting required primitive for condition '${condition.operator}' on column '${condition.column}' from '${condition.value}'")
        return condition.value?.jsonPrimitive ?: error(
            "Operator ${condition.operator} requires a value"
        )
    }


    private fun between(
        column: Column<*>,
        value: JsonElement?
    ): Op<Boolean> {
        logger.debug("Building BETWEEN predicate for column '${column.name}' with value: $value")
        val array =
            value as? JsonArray ?: error(
                "BETWEEN requires [from, to]"
            )

        require(array.size == 2) {
            "BETWEEN requires exactly two values"
        }

        val from = parseValue(column, array[0])
        val to = parseValue(column, array[1])
        logger.debug("Parsed BETWEEN range for column '${column.name}': from=$from, to=$to")

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
        val opName = if (negate) Pagination.Filter.Operator.NOT_IN else Pagination.Filter.Operator.IN
        logger.debug("Building $opName predicate for column '${column.name}' with value: $value")
        val array =
            value as? JsonArray ?: error(
                "$opName requires an array"
            )

        require(array.isNotEmpty()) {
            "IN / NOT_IN requires at least one value"
        }

        val values = array.map {
            parseValue(column, it)
        }
        logger.debug("Parsed $opName values for column '${column.name}': $values")

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
    ): Op<Boolean> {
        logger.debug("Building null comparison for column '${column.name}': isNull=$isNull")
        return object : Op<Boolean>() {

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
    }


    @Suppress("UNCHECKED_CAST")
    private fun comparison(
        column: Column<*>,
        operator: String,
        value: Any
    ): Op<Boolean> {
        logger.debug("Building comparison predicate: ${column.name} $operator $value")
        return object : Op<Boolean>() {

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
    }


    private fun like(
        column: Column<*>,
        value: String
    ): Op<Boolean> {
        logger.debug("Building LIKE predicate for column '${column.name}' with pattern '$value'")
        return comparison(
            column = column,
            operator = "LIKE",
            value = value
        )
    }


    @Suppress("UNCHECKED_CAST")
    private fun registerArgument(
        queryBuilder: QueryBuilder,
        column: Column<*>,
        value: Any?
    ) {
        logger.debug("Registering SQL argument for column '${column.name}' (${column.columnType.sqlType()}): $value")
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
        logger.debug("Encoding cursor for sort: $sort")

        val values = sort.map { item ->
            val col = table.column(item.column)
            val value = row[col]

            require(value != null) {
                "Cannot create a cursor from a NULL sort value " +
                        "for column ${item.column}"
            }

            logger.debug("Extracted sort value for column '${item.column}': $value")
            value.toString()
        }

        val payload =
            CursorPayload(
                sort = sort,
                values = values
            )

        val jsonValue =
            json.encodeToString(payload)
        logger.debug("Cursor payload JSON: $jsonValue")

        val encoded = Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                jsonValue.toByteArray(Charsets.UTF_8)
            )
        logger.debug("Encoded cursor string: $encoded")
        return encoded
    }


    private fun decodeCursor(
        value: String
    ): Cursor {
        logger.debug("Decoding cursor string: $value")

        val decoded = try {
            val s = String(
                Base64
                    .getUrlDecoder()
                    .decode(value),
                Charsets.UTF_8
            )
            logger.debug("Decoded Base64 cursor to JSON: $s")
            s
        } catch (cause: IllegalArgumentException) {
            logger.debug("Failed to decode Base64 cursor: $value", cause)
            throw IllegalArgumentException(
                "Invalid pagination cursor",
                cause
            )
        }

        val payload = try {
            json.decodeFromString<CursorPayload>(decoded)
        } catch (cause: Exception) {
            logger.debug("Failed to deserialize cursor payload JSON: $decoded", cause)
            throw IllegalArgumentException(
                "Invalid pagination cursor",
                cause
            )
        }

        val cursor = Cursor(
            sort = payload.sort,
            values = payload.values
        )
        logger.debug("Successfully decoded cursor: $cursor")
        return cursor
    }


    private fun validateCursor(
        sort: List<Pagination.Sort>,
        cursor: Cursor?
    ) {
        logger.debug("Validating cursor: $cursor against sort: $sort")

        if (cursor == null) {
            logger.debug("Cursor is null, validation skipped")
            return
        }

        require(cursor.sort == sort) {
            "Cursor was created for a different sort order"
        }

        require(cursor.values.size == sort.size) {
            "Cursor contains ${cursor.values.size} values " +
                    "but sort contains ${sort.size} columns"
        }
        logger.debug("Cursor validation successful")
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
        logger.debug("Compiling keyset cursor predicate for sort: $sort, cursor: $cursor")

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

                        val parsedPrevValue = parseCursorValue(
                            previousColumn,
                            previousValue
                        )
                        logger.debug("Cursor branch $index: equality on column '${previousColumn.name}' = $parsedPrevValue")

                        comparison(
                            column = previousColumn,
                            operator = "=",
                            value = parsedPrevValue
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

            val parsedCurrValue = parseCursorValue(
                currentColumn,
                currentValue
            )
            logger.debug("Cursor branch $index: comparison on column '${currentColumn.name}' $operator $parsedCurrValue")

            val currentComparison =
                comparison(
                    column = currentColumn,
                    operator = operator,
                    value = parsedCurrValue
                )

            (equalities + currentComparison)
                .compoundAnd()
        }

        logger.debug("Compiled ${branches.size} cursor branches into compound OR")
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
        logger.debug("Parsing JSON element value for column '${column.name}': $value")
        return parseStringValue(
            column = column,
            value = value.jsonPrimitive.content
        )
    }


    private fun parseCursorValue(
        column: Column<*>,
        value: String
    ): Any {
        logger.debug("Parsing cursor string value for column '${column.name}': $value")
        return parseStringValue(
            column = column,
            value = value
        )
    }


    private fun parseStringValue(
        column: Column<*>,
        value: String
    ): Any {
        val columnType = column.columnType

        if (columnType is EnumerationNameColumnType<*>) {
            val enumClass = columnType.klass.java

            return enumClass.enumConstants
                ?.firstOrNull { it.name == value }
                ?: error(
                    "Invalid value '$value' for enum column '${column.name}'. " +
                            "Expected one of: ${enumClass.enumConstants?.joinToString()}"
                )
        }

        val sqlType = columnType
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

            else -> error(
                "Unsupported query column type " +
                        "${column.columnType.sqlType()} " +
                        "for column ${column.name}"
            )
        }
    }
}
