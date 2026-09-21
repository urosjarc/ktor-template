package com.urosjarc.ktor_template.app.libs.pagination

import com.urosjarc.ktor_template.app.libs.pagination.cursor.PaginationCursor
import com.urosjarc.ktor_template.app.libs.pagination.cursor.PaginationCursorBase64
import com.urosjarc.ktor_template.app.libs.pagination.cursor.PaginationCursorCodec
import com.urosjarc.ktor_template.app.libs.pagination.setup.PaginationDefinition
import com.urosjarc.ktor_template.app.libs.pagination.outputs.PaginationPage
import com.urosjarc.ktor_template.app.libs.pagination.outputs.PaginationQueryResult
import com.urosjarc.ktor_template.app.libs.pagination.setup.PaginationField
import com.urosjarc.ktor_template.app.libs.pagination.setup.PaginationType
import com.urosjarc.ktor_template.core.types.Pagination
import com.urosjarc.ktor_template.core.types.encrypted
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.BasicBinaryColumnType
import org.jetbrains.exposed.v1.core.BasicUuidColumnType
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.ByteColumnType
import org.jetbrains.exposed.v1.core.CharacterColumnType
import org.jetbrains.exposed.v1.core.DecimalColumnType
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.EntityIDColumnType
import org.jetbrains.exposed.v1.core.EnumerationNameColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.FloatColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.ShortColumnType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.StringColumnType
import org.jetbrains.exposed.v1.core.UByteColumnType
import org.jetbrains.exposed.v1.core.UIntegerColumnType
import org.jetbrains.exposed.v1.core.ULongColumnType
import org.jetbrains.exposed.v1.core.UShortColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.datetime.DurationColumnType
import org.jetbrains.exposed.v1.core.datetime.InstantColumnType
import org.jetbrains.exposed.v1.core.datetime.LocalDateColumnType
import org.jetbrains.exposed.v1.core.datetime.LocalDateTimeColumnType
import org.jetbrains.exposed.v1.core.datetime.LocalTimeColumnType
import org.jetbrains.exposed.v1.core.datetime.OffsetDateTimeColumnType
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.r2dbc.Query
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID
import kotlin.collections.all
import kotlin.time.Duration

public class PaginationCompiler(
    private val definition: PaginationDefinition,
    private val cursorCodec: PaginationCursorCodec = PaginationCursorBase64,
) {

    public suspend fun apply(request: Pagination, source: Query): PaginationQueryResult {
        require(request.limit > 0) { "limit must be greater than zero" }

        val sort = normalizeSort(request.sort)
        validateSortPlacement(sort)

        val cursor = request.cursor?.let(cursorCodec::decode)
        validateCursor(
            sort = sort,
            where = request.where,
            having = request.having,
            cursor = cursor,
        )

        val query = source
            .apply {
                request.where?.let { where ->
                    adjustWhere {
                        val expression = compileWhere(where)
                        this?.and(expression) ?: expression
                    }
                }

                request.having?.let { having ->
                    adjustHaving {
                        val expression = compileHaving(having)
                        this?.and(expression) ?: expression
                    }
                }

                cursor?.let {
                    val cursorExpression = compileCursor(sort, it)
                    when (cursorPlacement(sort)) {
                        PaginationField.Place.WHERE -> adjustWhere {
                            this?.and(cursorExpression) ?: cursorExpression
                        }

                        PaginationField.Place.HAVING -> adjustHaving {
                            this?.and(cursorExpression) ?: cursorExpression
                        }
                    }
                }
            }
            .orderBy(*compileSort(sort))
            .limit(request.limit + 1)

        val rows = query.toList()
        val hasNext = rows.size > request.limit
        val page = if (hasNext) rows.take(request.limit) else rows
        val nextCursor = if (hasNext) {
            page.lastOrNull()?.let { encodeCursor(sort, request.where, request.having, it) }
        } else {
            null
        }

        return PaginationQueryResult(query, PaginationPage(data = page, nextCursor = nextCursor))
    }

    private fun canonicalize(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> buildJsonObject {
                element.keys.sorted().forEach { key ->
                    put(key, canonicalize(element.getValue(key)))
                }
            }

            is JsonArray -> JsonArray(element.map(::canonicalize))
            is JsonPrimitive -> element
        }

    private fun queryHash(
        sort: List<Pagination.Sort>,
        where: Pagination.Filter?,
        having: Pagination.Filter?,
    ): String {
        val json = buildJsonObject {
            put("sort", Json.encodeToJsonElement(sort))
            put("where", Json.encodeToJsonElement(where))
            put("having", Json.encodeToJsonElement(having))
        }

        return MessageDigest.getInstance("SHA-256")
            .digest(canonicalize(json).toString().encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun validateCursor(
        sort: List<Pagination.Sort>,
        where: Pagination.Filter?,
        having: Pagination.Filter?,
        cursor: PaginationCursor?,
    ) {
        if (cursor == null) return

        require(cursor.queryHash == queryHash(sort, where, having)) {
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
        require(sort.all { definition.fields.containsKey(it.column) }) {
            "All sort fields must exist in pagination fields"
        }

        return sort + definition.uniqueKey
            .filterNot { key -> sort.any { it.column == key } }
            .map { Pagination.Sort(it, Pagination.Sort.Direction.ASC) }
    }

    private fun validateSortPlacement(sort: List<Pagination.Sort>) {
        val placements = sort
            .map { itField(it.column).filterPlacement }
            .distinct()

        require(placements.size == 1) {
            "All sort fields must use the same filter placement"
        }
    }

    private fun cursorPlacement(sort: List<Pagination.Sort>): PaginationField.Place =
        itField(sort.first().column).filterPlacement

    private fun compileSort(
        sort: List<Pagination.Sort>,
    ): Array<Pair<Expression<*>, SortOrder>> = sort.map {
        itField(it.column).expression to when (it.direction) {
            Pagination.Sort.Direction.ASC -> SortOrder.ASC_NULLS_LAST
            Pagination.Sort.Direction.DESC -> SortOrder.DESC_NULLS_LAST
        }
    }.toTypedArray()

    private fun compileWhere(filter: Pagination.Filter): Op<Boolean> =
        compileFilter(filter, PaginationField.Place.WHERE)

    private fun compileHaving(filter: Pagination.Filter): Op<Boolean> =
        compileFilter(filter, PaginationField.Place.HAVING)

    private fun compileFilter(filter: Pagination.Filter, placement: PaginationField.Place): Op<Boolean> =
        when (filter) {
            is Pagination.Filter.Condition ->
                compileCondition(filter, placement)

            is Pagination.Filter.And ->
                filter.filters
                    .map { compileFilter(it, placement) }
                    .reduce { a, b -> a and b }

            is Pagination.Filter.Or ->
                filter.filters
                    .map { compileFilter(it, placement) }
                    .reduce { a, b -> a or b }
        }

    private fun compileCondition(
        condition: Pagination.Filter.Condition,
        placement: PaginationField.Place,
    ): Op<Boolean> {
        val field = itField(condition.column)

        require(field.filterPlacement == placement) {
            "Field '${condition.column}' is not valid in $placement"
        }
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

    private fun encodeCursorValue(
        field: PaginationField<*>,
        value: Any?,
    ): String? {
        if (value == null) return null

        return when (field.resolvedPaginationType) {
            PaginationType.ENCRYPTED,
            PaginationType.BINARY ->
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(value as ByteArray)

            else -> value.toString()
        }
    }

    private fun encodeCursor(
        sort: List<Pagination.Sort>,
        where: Pagination.Filter?,
        having: Pagination.Filter?,
        row: ResultRow,
    ): String = cursorCodec.encode(
        PaginationCursor(
            queryHash = queryHash(sort, where, having),
            sort = sort,
            values = sort.map {
                val field = itField(it.column)
                encodeCursorValue(field, row[field.expression])
            },
        )
    )

    private fun compileCursor(sort: List<Pagination.Sort>, cursor: PaginationCursor): Op<Boolean> {
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

    private fun parseValue(field: PaginationField<*>, value: JsonElement): Any {
        val raw = value.jsonPrimitive.content

        return when (field.resolvedPaginationType) {
            PaginationType.ENCRYPTED -> raw.encrypted().value
            else -> parseStringValue(field, raw)
        }
    }

    private fun parseCursorValue(
        field: PaginationField<*>,
        value: String,
    ): Any {
        return when (field.resolvedPaginationType) {
            PaginationType.ENCRYPTED,
            PaginationType.BINARY ->
                Base64.getUrlDecoder().decode(value)

            else ->
                parseStringValue(field, value)
        }
    }

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

            is DurationColumnType<*> -> Duration.parse(value)

            is LocalDateColumnType<*> -> LocalDate.parse(value)

            is LocalTimeColumnType<*> -> LocalTime.parse(value)

            is LocalDateTimeColumnType<*> -> LocalDateTime.parse(value)

            is InstantColumnType<*> -> Instant.parse(value)

            is OffsetDateTimeColumnType<*> -> OffsetDateTime.parse(value)

            else -> value
        }
    }

    private fun itField(name: String): PaginationField<*> = definition.fields[name] ?: error("Unknown pagination field: $name")
}
