package com.urosjarc.ktor_template.app.libs.pagination.outputs

import com.urosjarc.ktor_template.app.libs.pagination.setup.PaginationDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.Query
import java.math.BigDecimal
import java.util.UUID
import kotlin.collections.iterator

public data class PaginationQueryResult(
    val query: Query,
    val page: PaginationPage<ResultRow>,
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
