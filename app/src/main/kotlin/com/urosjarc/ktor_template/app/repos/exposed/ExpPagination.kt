package com.urosjarc.ktor_template.app.repos.exposed

import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.Query
import java.util.*

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
            ASC,
            DESC,
        }
    }

    @Serializable
    public sealed interface Filter {
        @Serializable
        public data class Condition(
            val column: String,
            val operator: Operator,
            val value: JsonElement? = null,
        ) : Filter

        @Serializable
        public data class And(
            val filters: List<Filter>,
        ) : Filter

        @Serializable
        public data class Or(
            val filters: List<Filter>,
        ) : Filter

        @Serializable
        public enum class Operator {
            EQ,
            NE,
            GT,
            GTE,
            LT,
            LTE,
            BETWEEN,
            CONTAINS,
            STARTS_WITH,
            ENDS_WITH,
            IN,
            NOT_IN,
            IS_NULL,
            IS_NOT_NULL,
        }
    }

    public data class Page<T>(
        val data: List<T>,
        val nextCursor: String?,
    )
}

/**
 * Describes the fields exposed to the pagination API.
 *
 * The expression can be:
 * - a normal table column;
 * - a column from a joined table;
 * - an aliased expression;
 * - an aggregate such as SUM(...).alias("total");
 * - a computed expression.
 *
 * [filterPlacement] controls where predicates for this field are emitted and
 * also tells cursor compilation whether the keyset predicate belongs in
 * WHERE or HAVING.
 *
 * The expression must be part of the SELECT list of the source query if it is
 * used for sorting or if a cursor has to be created from it.
 */
public data class PaginationField<T>(
    public val expression: Expression<T>,
    public val columnType: IColumnType<T & Any>,
    public val filterPlacement: FilterPlacement = FilterPlacement.WHERE,
)

public enum class FilterPlacement {
    WHERE,
    HAVING,
}

/**
 * Defines the API-visible pagination fields for one screen/query.
 *
 * [uniqueKey] must identify one logical row uniquely and should normally be
 * non-null. All missing unique-key fields are appended to the requested sort
 * as deterministic tie-breakers.
 */
public data class PaginationDefinition(
    public val fields: Map<String, PaginationField<*>>,
    public val uniqueKey: List<String>,
    public val defaultSort: List<Pagination.Sort> = uniqueKey.map {
        Pagination.Sort(it, Pagination.Sort.Direction.ASC)
    },
) {
    init {
        require(fields.isNotEmpty()) { "Pagination definition must contain at least one field" }
        require(uniqueKey.isNotEmpty()) { "Pagination definition must contain a unique key" }
        require(uniqueKey.all(fields::containsKey)) {
            "All unique key fields must exist in pagination fields"
        }
        require(defaultSort.isNotEmpty()) { "Default sort must contain at least one field" }
        require(defaultSort.all { fields.containsKey(it.column) }) {
            "All default sort fields must exist in pagination fields"
        }
    }

    public companion object {
        /**
         * Convenient factory for an Exposed typed expression.
         */
        public fun <T> field(
            expression: ExpressionWithColumnType<T>,
            filterPlacement: FilterPlacement = FilterPlacement.WHERE,
        ): PaginationField<T> = PaginationField(
            expression = expression,
            columnType = expression.columnType,
            filterPlacement = filterPlacement,
        )
    }
}

/**
 * The decoded, backend-internal representation of a cursor.
 *
 * Values are strings because they need to survive serialization without
 * coupling the cursor format to a particular database/JVM type hierarchy.
 */
public data class PaginationCursor(
    public val sort: List<Pagination.Sort>,
    public val values: List<String?>,
)

/**
 * Cursor encoding is intentionally replaceable.
 *
 * The default implementation is plain Base64. Applications that need
 * confidentiality or tamper protection can provide their own implementation,
 * for example one backed by authenticated encryption or an HMAC.
 */
public interface CursorCodec {
    public fun encode(cursor: PaginationCursor): String
    public fun decode(value: String): PaginationCursor
}

/**
 * Default cursor representation:
 *
 * JSON -> UTF-8 -> Base64 (without padding)
 *
 * Base64 is transport encoding, not encryption and not integrity protection.
 */
public class Base64CursorCodec(
    private val json: Json = Json,
) : CursorCodec {
    @Serializable
    private data class Payload(
        val sort: List<Pagination.Sort>,
        val values: List<String?>,
    )

    override fun encode(cursor: PaginationCursor): String {
        val payload = Payload(
            sort = cursor.sort,
            values = cursor.values,
        )

        val encodedJson = json.encodeToString(payload)
        return Base64.getEncoder()
            .withoutPadding()
            .encodeToString(encodedJson.toByteArray(Charsets.UTF_8))
    }

    override fun decode(value: String): PaginationCursor {
        val decoded = try {
            String(
                Base64.getDecoder().decode(value),
                Charsets.UTF_8,
            )
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid pagination cursor", cause)
        }

        val payload = try {
            json.decodeFromString<Payload>(decoded)
        } catch (cause: Exception) {
            throw IllegalArgumentException("Invalid pagination cursor", cause)
        }

        return PaginationCursor(
            sort = payload.sort,
            values = payload.values,
        )
    }
}

/**
 * Generic keyset-pagination compiler for Exposed R2DBC queries.
 *
 * The source query owns the dataset and may contain arbitrary joins,
 * subqueries, GROUP BY, aggregates, and computed expressions.
 *
 * The compiler only adds:
 * - client-visible filtering;
 * - cursor predicate;
 * - deterministic ORDER BY;
 * - LIMIT + 1;
 * - next-cursor creation.
 */
public class PaginationCompiler(
    private val definition: PaginationDefinition,
    private val cursorCodec: CursorCodec = Base64CursorCodec(),
    private val maxLimit: Int = 100,
) {
    init {
        require(maxLimit > 0) { "maxLimit must be greater than zero" }
    }

    public data class Result(
        val query: Query,
        val page: Pagination.Page<ResultRow>,
    )

    public suspend fun apply(
        request: Pagination,
        source: Query,
    ): Result {
        require(request.limit in 1..maxLimit) {
            "limit must be between 1 and $maxLimit"
        }

        val sort = normalizeSort(request.sort)
        val cursor = request.cursor?.let(cursorCodec::decode)

        validateCursor(sort, cursor)

        val filterPlan = request.filter?.let(::compileFilter)
            ?: FilterPlan()

        val cursorPlan = cursor?.let {
            compileCursor(
                sort = sort,
                cursor = it,
            )
        }

        var query = source

        val wherePredicates = buildList {
            filterPlan.where?.let(::add)
            cursorPlan?.takeIf { it.placement == FilterPlacement.WHERE }?.predicate?.let(::add)
        }

        if (wherePredicates.isNotEmpty()) {
            query = query.where { wherePredicates.compoundAnd() }
        }

        val havingPredicates = buildList {
            filterPlan.having?.let(::add)
            cursorPlan?.takeIf { it.placement == FilterPlacement.HAVING }?.predicate?.let(::add)
        }

        if (havingPredicates.isNotEmpty()) {
            query = query.having { havingPredicates.compoundAnd() }
        }

        query = query
            .orderBy(*compileSort(sort))
            .limit(request.limit + 1)

        val rows = query.toList()
        val hasNext = rows.size > request.limit
        val page = if (hasNext) rows.take(request.limit) else rows

        val nextCursor = if (hasNext) {
            page.lastOrNull()?.let { row ->
                encodeCursor(sort, row)
            }
        } else {
            null
        }

        return Result(
            query = query,
            page = Pagination.Page(
                data = page,
                nextCursor = nextCursor,
            ),
        )
    }

    private fun normalizeSort(
        requested: List<Pagination.Sort>,
    ): List<Pagination.Sort> {
        val base = if (requested.isEmpty()) definition.defaultSort else requested

        base.forEach { sort ->
            require(definition.fields.containsKey(sort.column)) {
                "Unknown pagination field: ${sort.column}"
            }
        }

        val result = base.toMutableList()

        for (uniqueKeyField in definition.uniqueKey) {
            if (result.none { it.column == uniqueKeyField }) {
                result += Pagination.Sort(
                    column = uniqueKeyField,
                    direction = Pagination.Sort.Direction.ASC,
                )
            }
        }

        return result
    }

    /**
     * NULLS LAST is a hard pagination invariant.
     *
     * Each sort expression becomes:
     *
     *     CASE WHEN expr IS NULL THEN 1 ELSE 0 END ASC,
     *     expr ASC|DESC
     *
     * The CASE term makes null placement independent of the database's
     * default NULL ordering.
     */
    private fun compileSort(
        sort: List<Pagination.Sort>,
    ): Array<Pair<Expression<*>, SortOrder>> {
        return sort.flatMap { item ->
            val field = definition.fields[item.column]
                ?: error("Unknown pagination field: ${item.column}")

            val order = when (item.direction) {
                Pagination.Sort.Direction.ASC -> SortOrder.ASC
                Pagination.Sort.Direction.DESC -> SortOrder.DESC
            }

            listOf(
                nullRank(field.expression) to SortOrder.ASC,
                field.expression to order,
            )
        }.toTypedArray()
    }

    private fun nullRank(
        expression: Expression<*>,
    ): Expression<Int> = object : Expression<Int>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append("CASE WHEN ")
            queryBuilder.append(expression)
            queryBuilder.append(" IS NULL THEN 1 ELSE 0 END")
        }
    }

    // ========================================================================
    // FILTER
    // ========================================================================

    private data class FilterPlan(
        val where: Op<Boolean>? = null,
        val having: Op<Boolean>? = null,
    )

    private fun compileFilter(
        filter: Pagination.Filter,
    ): FilterPlan = when (filter) {
        is Pagination.Filter.Condition -> {
            val field = requireField(filter.column)
            val predicate = compileCondition(field, filter)

            when (field.filterPlacement) {
                FilterPlacement.WHERE -> FilterPlan(where = predicate)
                FilterPlacement.HAVING -> FilterPlan(having = predicate)
            }
        }

        is Pagination.Filter.And -> {
            require(filter.filters.isNotEmpty()) { "AND requires at least one filter" }
            val plans = filter.filters.map(::compileFilter)

            FilterPlan(
                where = plans.mapNotNull { it.where }.takeIf(List<*>::isNotEmpty)?.compoundAnd(),
                having = plans.mapNotNull { it.having }.takeIf(List<*>::isNotEmpty)?.compoundAnd(),
            )
        }

        is Pagination.Filter.Or -> {
            require(filter.filters.isNotEmpty()) { "OR requires at least one filter" }
            val plans = filter.filters.map(::compileFilter)

            val hasWhere = plans.any { it.where != null }
            val hasHaving = plans.any { it.having != null }

            require(!(hasWhere && hasHaving)) {
                "OR cannot mix WHERE and HAVING filters"
            }

            when {
                hasWhere -> FilterPlan(
                    where = plans.mapNotNull { it.where }.compoundOr(),
                )

                hasHaving -> FilterPlan(
                    having = plans.mapNotNull { it.having }.compoundOr(),
                )

                else -> error("OR contains no predicates")
            }
        }
    }

    private fun compileCondition(
        field: PaginationField<*>,
        condition: Pagination.Filter.Condition,
    ): Op<Boolean> {
        val expression = field.expression

        return when (condition.operator) {
            Pagination.Filter.Operator.EQ ->
                comparison(expression, field.columnType, "=", requiredValue(field, condition))

            Pagination.Filter.Operator.NE ->
                comparison(expression, field.columnType, "<>", requiredValue(field, condition))

            Pagination.Filter.Operator.GT ->
                comparison(expression, field.columnType, ">", requiredValue(field, condition))

            Pagination.Filter.Operator.GTE ->
                comparison(expression, field.columnType, ">=", requiredValue(field, condition))

            Pagination.Filter.Operator.LT ->
                comparison(expression, field.columnType, "<", requiredValue(field, condition))

            Pagination.Filter.Operator.LTE ->
                comparison(expression, field.columnType, "<=", requiredValue(field, condition))

            Pagination.Filter.Operator.BETWEEN ->
                between(expression, field.columnType, condition.value)

            Pagination.Filter.Operator.CONTAINS ->
                comparison(
                    expression,
                    field.columnType,
                    "LIKE",
                    "%${requiredPrimitive(condition).content}%",
                )

            Pagination.Filter.Operator.STARTS_WITH ->
                comparison(
                    expression,
                    field.columnType,
                    "LIKE",
                    "${requiredPrimitive(condition).content}%",
                )

            Pagination.Filter.Operator.ENDS_WITH ->
                comparison(
                    expression,
                    field.columnType,
                    "LIKE",
                    "%${requiredPrimitive(condition).content}",
                )

            Pagination.Filter.Operator.IN ->
                inList(expression, field.columnType, condition.value, negate = false)

            Pagination.Filter.Operator.NOT_IN ->
                inList(expression, field.columnType, condition.value, negate = true)

            Pagination.Filter.Operator.IS_NULL ->
                nullComparison(expression, isNull = true)

            Pagination.Filter.Operator.IS_NOT_NULL ->
                nullComparison(expression, isNull = false)
        }
    }

    private fun requireField(name: String): PaginationField<*> =
        definition.fields[name] ?: error("Unknown pagination field: $name")

    private fun requiredValue(
        field: PaginationField<*>,
        condition: Pagination.Filter.Condition,
    ): Any {
        val value = condition.value
            ?: error("Operator ${condition.operator} requires a value")

        return parseValue(field.columnType, value)
    }

    private fun requiredPrimitive(
        condition: Pagination.Filter.Condition,
    ) = condition.value?.jsonPrimitive
        ?: error("Operator ${condition.operator} requires a value")

    private fun between(
        expression: Expression<*>,
        columnType: IColumnType<*>,
        value: JsonElement?,
    ): Op<Boolean> {
        val array = value as? JsonArray
            ?: error("BETWEEN requires [from, to]")

        require(array.size == 2) {
            "BETWEEN requires exactly two values"
        }

        val from = parseValue(columnType, array[0])
        val to = parseValue(columnType, array[1])

        return object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append(expression)
                queryBuilder.append(" BETWEEN ")
                registerArgument(queryBuilder, columnType, from)
                queryBuilder.append(" AND ")
                registerArgument(queryBuilder, columnType, to)
            }
        }
    }

    private fun inList(
        expression: Expression<*>,
        columnType: IColumnType<*>,
        value: JsonElement?,
        negate: Boolean,
    ): Op<Boolean> {
        val array = value as? JsonArray
            ?: error("IN / NOT_IN requires an array")

        require(array.isNotEmpty()) {
            "IN / NOT_IN requires at least one value"
        }

        val values = array.map { parseValue(columnType, it) }

        return object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append(expression)
                queryBuilder.append(if (negate) " NOT IN (" else " IN (")

                values.forEachIndexed { index, item ->
                    if (index > 0) queryBuilder.append(", ")
                    registerArgument(queryBuilder, columnType, item)
                }

                queryBuilder.append(")")
            }
        }
    }

    private fun nullComparison(
        expression: Expression<*>,
        isNull: Boolean,
    ): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append(expression)
            queryBuilder.append(if (isNull) " IS NULL" else " IS NOT NULL")
        }
    }

    private fun comparison(
        expression: Expression<*>,
        columnType: IColumnType<*>,
        operator: String,
        value: Any,
    ): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append(expression)
            queryBuilder.append(" ")
            queryBuilder.append(operator)
            queryBuilder.append(" ")
            registerArgument(queryBuilder, columnType, value)
        }
    }

    private fun registerArgument(
        queryBuilder: QueryBuilder,
        columnType: IColumnType<*>,
        value: Any?,
    ) {
        @Suppress("UNCHECKED_CAST")
        queryBuilder.registerArgument(
            columnType as IColumnType<Any>,
            value,
        )
    }

    // ========================================================================
    // CURSOR
    // ========================================================================

    private fun encodeCursor(
        sort: List<Pagination.Sort>,
        row: ResultRow,
    ): String {
        val values = sort.map { sortItem ->
            val field = requireField(sortItem.column)
            @Suppress("UNCHECKED_CAST")
            val expression = field.expression as Expression<Any?>
            row[expression]?.toString()
        }

        return cursorCodec.encode(
            PaginationCursor(
                sort = sort,
                values = values,
            )
        )
    }

    private fun validateCursor(
        sort: List<Pagination.Sort>,
        cursor: PaginationCursor?,
    ) {
        if (cursor == null) return

        require(cursor.sort == sort) {
            "Cursor was created for a different sort order"
        }

        require(cursor.values.size == sort.size) {
            "Cursor contains ${cursor.values.size} values but sort contains ${sort.size} columns"
        }
    }

    /**
     * Builds a lexicographic keyset predicate for the effective ORDER BY.
     *
     * NULLS LAST rules:
     * - a non-null cursor value may advance to a larger/smaller value OR to NULL;
     * - a NULL cursor value cannot advance using that field itself;
     * - later unique-key fields still advance rows within the NULL group.
     */
    private data class CursorPlan(
        val predicate: Op<Boolean>,
        val placement: FilterPlacement,
    )

    private fun compileCursor(
        sort: List<Pagination.Sort>,
        cursor: PaginationCursor,
    ): CursorPlan {
        val branches = sort.indices.mapNotNull { index ->
            val equalities = sort.indices
                .take(index)
                .map { previousIndex ->
                    val previousItem = sort[previousIndex]
                    val previousField = requireField(previousItem.column)
                    val previousValue = cursor.values[previousIndex]

                    if (previousValue == null) {
                        nullComparison(previousField.expression, isNull = true)
                    } else {
                        comparison(
                            previousField.expression,
                            previousField.columnType,
                            "=",
                            parseCursorValue(previousField.columnType, previousValue),
                        )
                    }
                }

            val currentItem = sort[index]
            val currentField = requireField(currentItem.column)
            val currentValue = cursor.values[index]

            val currentAfter = if (currentValue == null) {
                // NULL is already the last value for this sort position.
                // There is no row strictly after it based on this field.
                null
            } else {
                val operator = when (currentItem.direction) {
                    Pagination.Sort.Direction.ASC -> ">"
                    Pagination.Sort.Direction.DESC -> "<"
                }

                val valuePredicate = comparison(
                    currentField.expression,
                    currentField.columnType,
                    operator,
                    parseCursorValue(currentField.columnType, currentValue),
                )

                listOf(
                    valuePredicate,
                    nullComparison(
                        currentField.expression,
                        isNull = true,
                    ),
                ).compoundOr()
            }

            currentAfter?.let { (equalities + it).compoundAnd() }
        }

        require(branches.isNotEmpty()) {
            "Cursor does not define a position after which rows can be fetched"
        }

        return CursorPlan(
            predicate = branches.compoundOr(),
            placement = if (sort.any { requireField(it.column).filterPlacement == FilterPlacement.HAVING }) {
                FilterPlacement.HAVING
            } else {
                FilterPlacement.WHERE
            },
        )
    }

    // ========================================================================
    // VALUE PARSING
    // ========================================================================

    private fun parseValue(
        columnType: IColumnType<*>,
        value: JsonElement,
    ): Any = parseStringValue(columnType, value.jsonPrimitive.content)

    private fun parseCursorValue(
        columnType: IColumnType<*>,
        value: String,
    ): Any = parseStringValue(columnType, value)

    private fun parseStringValue(
        columnType: IColumnType<*>,
        value: String,
    ): Any {
        if (columnType is EnumerationNameColumnType<*>) {
            val enumClass = columnType.klass.java
            return enumClass.enumConstants
                ?.firstOrNull { it.name == value }
                ?: error(
                    "Invalid enum value '$value'. Expected one of: " +
                            enumClass.enumConstants?.joinToString(),
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
                    sqlType.startsWith("CLOB") -> value

            sqlType.startsWith("TINYINT") ||
                    sqlType.startsWith("BYTEINT") -> value.toByte()

            sqlType.startsWith("SMALLINT") -> value.toShort()

            sqlType.startsWith("INTEGER") ||
                    sqlType.startsWith("INT") -> value.toInt()

            sqlType.startsWith("BIGINT") -> value.toLong()

            sqlType.startsWith("REAL") -> value.toFloat()

            sqlType.startsWith("DOUBLE") ||
                    sqlType.startsWith("FLOAT") -> value.toDouble()

            sqlType.startsWith("DECIMAL") ||
                    sqlType.startsWith("NUMERIC") -> value.toBigDecimal()

            sqlType.startsWith("BOOLEAN") -> value.toBooleanStrict()

            sqlType.startsWith("UUID") -> UUID.fromString(value)

            else -> error(
                "Unsupported pagination field type ${columnType.sqlType()}",
            )
        }
    }
}
