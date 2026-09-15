package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.core.types.Pagination
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.datetime.*
import org.jetbrains.exposed.v1.r2dbc.Query
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.*

public enum class PaginationType {
    ENUM, STRING, INTEGER, DECIMAL, BOOLEAN, UUID, DATE, DATETIME, BINARY, ENCRYPTED, HASHED;

    public companion object {
        public fun from(columnType: IColumnType<*>): PaginationType {
            val sqlType = columnType.sqlType().uppercase().trim()

            return when {
                sqlType.startsWith("VARCHAR") || sqlType.startsWith("CHAR") || sqlType.startsWith("TEXT") || sqlType.startsWith("CLOB") -> STRING

                sqlType.startsWith("TINYINT") || sqlType.startsWith("SMALLINT") || sqlType.startsWith("INTEGER") || sqlType.startsWith("INT") || sqlType.startsWith(
                    "BIGINT"
                ) -> INTEGER

                sqlType.startsWith("REAL") || sqlType.startsWith("DOUBLE") || sqlType.startsWith("FLOAT") || sqlType.startsWith("DECIMAL") || sqlType.startsWith(
                    "NUMERIC"
                ) -> DECIMAL

                sqlType.startsWith("BINARY") || sqlType.startsWith("VARBINARY") || sqlType.startsWith("BYTEA") || sqlType.startsWith("BLOB") -> BINARY

                sqlType.startsWith("TIMESTAMP") || sqlType.startsWith("DATETIME") -> DATETIME

                sqlType.startsWith("BOOLEAN") || sqlType.startsWith("BOOL") -> BOOLEAN

                sqlType.startsWith("UUID") -> UUID
                sqlType.startsWith("DATE") -> DATE

                columnType is EnumerationNameColumnType<*> -> ENUM

                else -> error(
                    "Cannot infer pagination type from SQL type: " + columnType.sqlType()
                )
            }
        }
    }
}

public enum class FilterPlacement { WHERE, HAVING }

public data class PaginationField<T>(
    public val expression: ExpressionWithColumnType<T>,
    public val paginationType: PaginationType? = null,
    public val filterPlacement: FilterPlacement = FilterPlacement.WHERE,
) {
    public val resolvedPaginationType: PaginationType
        get() = paginationType ?: PaginationType.from(expression.columnType)
}

public data class PaginationEntity(
    public val resource: String,
    public val primaryKey: String,
)

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
            filterPlacement: FilterPlacement = FilterPlacement.WHERE,
        ): PaginationField<T> = PaginationField(expression, paginationType, filterPlacement)
    }
}

public interface CursorCodec {
    public fun encode(cursor: Cursor): String
    public fun decode(value: String): Cursor
}

public data class Cursor(
    public val queryHash: String,
    public val sort: List<Pagination.Sort>,
    public val values: List<String?>,
)

public object Base64CursorCodec : CursorCodec {
    @Serializable
    private data class Payload(val queryHash: String, val sort: List<Pagination.Sort>, val values: List<String?>)

    private val json = Json

    override fun encode(cursor: Cursor): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        json.encodeToString(
            Payload(
                queryHash = cursor.queryHash,
                sort = cursor.sort,
                values = cursor.values,
            )
        ).encodeToByteArray()
    )

    override fun decode(value: String): Cursor {
        val decoded = try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid pagination cursor", cause)
        }
        val payload = try {
            json.decodeFromString<Payload>(decoded)
        } catch (cause: Exception) {
            throw IllegalArgumentException("Invalid pagination cursor", cause)
        }

        return Cursor(
            queryHash = payload.queryHash,
            sort = payload.sort,
            values = payload.values,
        )
    }
}

public class PaginationCompiler(
    private val definition: PaginationDefinition,
    private val cursorCodec: CursorCodec = Base64CursorCodec,
) {
    public data class Result(
        val query: Query,
        val page: Pagination.Page<ResultRow>,
    ) {
        public fun toJson(
            definition: PaginationDefinition,
        ): JsonArray = buildJsonArray {
            for (row in page.data) {
                add(
                    buildJsonObject {
                        for ((name, field) in definition.fields) {
                            val value = row[field.expression]

                            when (value) {
                                null -> put(name, JsonNull)
                                is String -> put(name, value)
                                is Int -> put(name, value)
                                is Long -> put(name, value)
                                is Boolean -> put(name, value)
                                is BigDecimal -> put(name, value.toString())
                                is UUID -> put(name, value.toString())
                                is Enum<*> -> put(name, value.name)
                                else -> put(name, value.toString())
                            }
                        }
                    })
            }
        }
    }

    public suspend fun apply(request: Pagination, source: Query): Result {
        require(request.limit > 0) { "limit must be greater than zero" }
        val sort = normalizeSort(request.sort)
        val cursor = request.cursor?.let(cursorCodec::decode)
        validateCursor(sort = sort, filter = request.filter, cursor = cursor)

        val filterExpression = request.filter?.let(::compileFilter)
        val cursorExpression = cursor?.let { compileCursor(sort, it) }
        val predicates = buildList {
            filterExpression?.let(::add)
            cursorExpression?.let(::add)
        }

        val query = source.let { query ->
            if (predicates.isEmpty()) {
                query
            } else {
                query.adjustWhere {
                    this?.and(predicates.compoundAnd()) ?: predicates.compoundAnd()
                }
            }
        }.orderBy(*compileSort(sort)).limit(request.limit + 1)

        val rows = query.toList()
        val hasNext = rows.size > request.limit
        val page = if (hasNext) rows.take(request.limit) else rows
        val nextCursor = if (hasNext) page.lastOrNull()?.let { encodeCursor(sort, request.filter, it) } else null

        return Result(query, Pagination.Page(data = page, nextCursor = nextCursor))
    }

    private fun queryHash(sort: List<Pagination.Sort>, filter: Pagination.Filter?): String {
        val payload = buildJsonObject {
            put("sort", Json.encodeToJsonElement(sort))
            put("filter", Json.encodeToJsonElement(filter))
        }
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(payload.toString().encodeToByteArray())
        )
        return MessageDigest.getInstance("SHA-256").digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateCursor(
        sort: List<Pagination.Sort>,
        filter: Pagination.Filter?,
        cursor: Cursor?,
    ) {
        if (cursor == null) return

        require(cursor.queryHash == queryHash(sort, filter)) {
            "Cursor does not belong to this query"
        }

        require(cursor.sort == sort) {
            "Cursor sort does not match requested sort"
        }

        require(cursor.values.size == sort.size) {
            "Cursor value count does not match sort fields"
        }
    }

    private fun normalizeSort(requested: List<Pagination.Sort>): List<Pagination.Sort> {
        val sort = if (requested.isEmpty()) definition.defaultSort else requested
        require(sort.all { definition.fields.containsKey(it.column) }) { "All sort fields must exist in pagination fields" }
        return if (sort.any { it.column in definition.uniqueKey }) sort
        else sort + definition.uniqueKey.map { Pagination.Sort(it, Pagination.Sort.Direction.ASC) }
    }

    private fun compileSort(
        sort: List<Pagination.Sort>,
    ): Array<Pair<Expression<*>, SortOrder>> = sort.map {
        itField(it.column).expression to when (it.direction) {
            Pagination.Sort.Direction.ASC -> SortOrder.ASC_NULLS_LAST
            Pagination.Sort.Direction.DESC -> SortOrder.DESC_NULLS_LAST
        }
    }.toTypedArray()

    private fun compileFilter(filter: Pagination.Filter): Op<Boolean> = when (filter) {
        is Pagination.Filter.Condition -> compileCondition(filter)
        is Pagination.Filter.And -> {
            require(filter.filters.isNotEmpty()) { "AND requires at least one filter" }
            filter.filters.map(::compileFilter).compoundAnd()
        }

        is Pagination.Filter.Or -> {
            require(filter.filters.isNotEmpty()) { "OR requires at least one filter" }
            filter.filters.map(::compileFilter).compoundOr()
        }
    }

    private fun compileCondition(condition: Pagination.Filter.Condition): Op<Boolean> {
        val field = itField(condition.column)
        return when (condition.operator) {
            Pagination.Filter.Operator.EQ -> comparison(field, "=", requiredValue(field, condition))
            Pagination.Filter.Operator.NE -> comparison(field, "<>", requiredValue(field, condition))
            Pagination.Filter.Operator.GT -> comparison(field, ">", requiredValue(field, condition))
            Pagination.Filter.Operator.GTE -> comparison(field, ">=", requiredValue(field, condition))
            Pagination.Filter.Operator.LT -> comparison(field, "<", requiredValue(field, condition))
            Pagination.Filter.Operator.LTE -> comparison(field, "<=", requiredValue(field, condition))
            Pagination.Filter.Operator.BETWEEN -> between(field, condition.value)
            Pagination.Filter.Operator.CONTAINS -> like(field, "%${requiredPrimitive(condition).content}%")
            Pagination.Filter.Operator.STARTS_WITH -> like(field, "${requiredPrimitive(condition).content}%")
            Pagination.Filter.Operator.ENDS_WITH -> like(field, "%${requiredPrimitive(condition).content}")
            Pagination.Filter.Operator.IN -> inList(field, condition.value, false)
            Pagination.Filter.Operator.NOT_IN -> inList(field, condition.value, true)
            Pagination.Filter.Operator.IS_NULL -> nullComparison(field.expression, true)
            Pagination.Filter.Operator.IS_NOT_NULL -> nullComparison(field.expression, false)
        }
    }

    private fun requiredValue(field: PaginationField<*>, condition: Pagination.Filter.Condition): Any =
        condition.value?.let { parseValue(field, it) } ?: error("Operator ${condition.operator} requires a value")

    private fun requiredPrimitive(condition: Pagination.Filter.Condition): JsonPrimitive =
        condition.value?.jsonPrimitive ?: error("Operator ${condition.operator} requires a value")

    private fun between(field: PaginationField<*>, value: JsonElement?): Op<Boolean> {
        val array = value as? JsonArray ?: error("BETWEEN requires [from, to]")
        require(array.size == 2) { "BETWEEN requires exactly two values" }
        val from = parseValue(field, array[0])
        val to = parseValue(field, array[1])
        return object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append(field.expression).append(" BETWEEN ")
                registerArgument(queryBuilder, field, from)
                queryBuilder.append(" AND ")
                registerArgument(queryBuilder, field, to)
            }
        }
    }

    private fun inList(field: PaginationField<*>, value: JsonElement?, negate: Boolean): Op<Boolean> {
        val name = if (negate) Pagination.Filter.Operator.NOT_IN else Pagination.Filter.Operator.IN
        val array = value as? JsonArray ?: error("$name requires an array")
        require(array.isNotEmpty()) { "IN / NOT_IN requires at least one value" }
        val values = array.map { parseValue(field, it) }
        return object : Op<Boolean>() {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) {
                queryBuilder.append(field.expression).append(if (negate) " NOT IN (" else " IN (")
                values.forEachIndexed { index, item ->
                    if (index > 0) queryBuilder.append(", ")
                    registerArgument(queryBuilder, field, item)
                }
                queryBuilder.append(")")
            }
        }
    }

    private fun nullComparison(expression: Expression<*>, isNull: Boolean): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append(expression).append(if (isNull) " IS NULL" else " IS NOT NULL")
        }
    }

    private fun comparison(field: PaginationField<*>, operator: String, value: Any): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append(field.expression).append(" $operator ")
            registerArgument(queryBuilder, field, value)
        }
    }

    private fun like(field: PaginationField<*>, value: String): Op<Boolean> = comparison(field, "LIKE", value)

    @Suppress("UNCHECKED_CAST")
    private fun registerArgument(queryBuilder: QueryBuilder, field: PaginationField<*>, value: Any?) {
        queryBuilder.registerArgument(field.expression.columnType as IColumnType<Any>, value)
    }

    private fun encodeCursor(sort: List<Pagination.Sort>, filter: Pagination.Filter?, row: ResultRow): String = cursorCodec.encode(
        Cursor(
            queryHash = queryHash(sort, filter),
            sort = sort,
            values = sort.map { row[itField(it.column).expression]?.toString() },
        )
    )

    private fun validateCursor(sort: List<Pagination.Sort>, cursor: Cursor?) {
        if (cursor == null) return
        require(cursor.sort == sort) { "Cursor was created for a different sort order" }
        require(cursor.values.size == sort.size) {
            "Cursor contains ${cursor.values.size} values but sort contains ${sort.size} columns"
        }
    }

    private fun compileCursor(sort: List<Pagination.Sort>, cursor: Cursor): Op<Boolean> {
        val branches = sort.indices.map { index ->
            val equalities = sort.indices.take(index).map { previousIndex ->
                val previousSort = sort[previousIndex]
                val previousField = itField(previousSort.column)
                val previousValue = cursor.values[previousIndex]
                if (previousValue == null) nullComparison(previousField.expression, true)
                else comparison(previousField, "=", parseCursorValue(previousField, previousValue))
            }

            val currentSort = sort[index]
            val currentField = itField(currentSort.column)
            val currentValue = cursor.values[index]
            val currentPredicate = if (currentValue == null) {
                nullComparison(currentField.expression, true)
            } else {
                val operator = when (currentSort.direction) {
                    Pagination.Sort.Direction.ASC -> ">"
                    Pagination.Sort.Direction.DESC -> "<"
                }
                listOf(
                    comparison(currentField, operator, parseCursorValue(currentField, currentValue)),
                    nullComparison(currentField.expression, true),
                ).compoundOr()
            }
            (equalities + currentPredicate).compoundAnd()
        }
        return branches.compoundOr()
    }

    private fun parseValue(field: PaginationField<*>, value: JsonElement): Any = parseStringValue(field, value.jsonPrimitive.content)

    private fun parseCursorValue(field: PaginationField<*>, value: String): Any = parseStringValue(field, value)

    private fun parseStringValue(field: PaginationField<*>, value: String): Any = parseStringValue(field.expression.columnType, value)

    private fun parseStringValue(
        columnType: IColumnType<*>,
        value: String,
    ): Any {
        if (columnType is EnumerationNameColumnType<*>) {
            val enumClass = columnType.klass.java
            return enumClass.enumConstants?.firstOrNull { it.name == value } ?: error(
                "Invalid enum value '$value'. " + "Expected one of: ${enumClass.enumConstants?.joinToString()}"
            )
        }

        if (columnType is EntityIDColumnType<*>) {
            return parseStringValue(columnType.idColumn.columnType, value).let { columnType.valueFromDB(it) }
        }

        return when (columnType) {
            is ByteColumnType -> value.toByte()
            is UByteColumnType -> value.toUByte()

            is ShortColumnType -> value.toShort()
            is UShortColumnType -> value.toUShort()

            is IntegerColumnType -> value.toInt()
            is UIntegerColumnType -> value.toUInt()

            is LongColumnType -> value.toLong()
            is ULongColumnType -> value.toULong()

            is FloatColumnType -> value.toFloat()
            is DoubleColumnType -> value.toDouble()

            is DecimalColumnType -> value.toBigDecimal()

            is BooleanColumnType -> value.toBooleanStrict()

            is CharacterColumnType -> {
                require(value.length == 1) { "Expected a single character, got '$value'" }
                value[0]
            }

            is StringColumnType -> value

            is BasicUuidColumnType -> UUID.fromString(value)

            is BasicBinaryColumnType -> value.toByteArray(Charsets.UTF_8)

            is DurationColumnType<*> -> kotlin.time.Duration.parse(value)

            is LocalDateColumnType<*> -> java.time.LocalDate.parse(value)

            is LocalTimeColumnType<*> -> java.time.LocalTime.parse(value)

            is LocalDateTimeColumnType<*> -> java.time.LocalDateTime.parse(value)

            is InstantColumnType<*> -> java.time.Instant.parse(value)

            is OffsetDateTimeColumnType<*> -> java.time.OffsetDateTime.parse(value)

            else -> value
        }
    }

    private fun itField(name: String): PaginationField<*> = definition.fields[name] ?: error("Unknown pagination field: $name")
}
