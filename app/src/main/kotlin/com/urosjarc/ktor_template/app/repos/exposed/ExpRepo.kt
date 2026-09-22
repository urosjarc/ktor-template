package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.app.libs.pagination.PaginationCompiler
import com.urosjarc.ktor_template.app.libs.pagination.setup.PaginationDefinition
import com.urosjarc.ktor_template.core.domain.Domain
import com.urosjarc.ktor_template.core.repos.Repo
import com.urosjarc.ktor_template.core.services.JsonService
import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.Pagination
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.r2dbc.*
import java.time.OffsetDateTime

internal abstract class ExpTable<T>(name: String, val serializer: KSerializer<T>) : UuidTable(name, uuidVersion = UuidVersion.V7) {
    abstract val paginationDefinition: PaginationDefinition
    val createdAt = timestampWithTimeZone("created_at")
    val insertedAt = timestampWithTimeZone("inserted_at") // DB managed columns (trigger based)
    val updatedAt = timestampWithTimeZone("updated_at")   // DB managed columns (trigger based)
    abstract fun toDomain(row: ResultRow): T
    abstract fun toRow(tx: UpdateBuilder<Int>, domain: T)
    fun toJson(row: ResultRow): JsonObject = JsonService.module.encodeToJsonElement(serializer = serializer, value = this.toDomain(row)).jsonObject
    fun toDomain(obj: JsonObject): T = JsonService.module.decodeFromJsonElement(deserializer = serializer, element = obj)
}

internal open class ExpRepo<T : Domain<T>>(val table: ExpTable<T>) : Repo<T> {
    override val tableName: String = table.tableName
    override fun decode(json: JsonObject): T = table.toDomain(json)

    override suspend fun createTable() {
        SchemaUtils.create(table)
    }

    override suspend fun count(): Long = table.selectAll().count()

    override suspend fun select(): List<T> = table.selectAll().map(table::toDomain).toList()

    override suspend fun select(id: Id<T>): T? = table.selectAll().where { table.id eq id.uuid }.singleOrNull()?.let(table::toDomain)

    override suspend fun select(ids: List<Id<T>>): List<T> {
        if (ids.isEmpty()) return emptyList()
        return table.selectAll().where { table.id inList ids.map { it.uuid } }.map(table::toDomain).toList()
    }

    override suspend fun select(pagination: Pagination): Pagination.Page<JsonObject> {
        val result = PaginationCompiler(definition = table.paginationDefinition).apply(request = pagination, source = table.selectAll())
        return Pagination.Page(
            data = result.page.data.map(table::toJson),
            nextCursor = result.page.nextCursor,
        )
    }

    override suspend fun insert(obj: T) {
        table.insert {
            it[table.id] = obj.id.uuid
            it[table.createdAt] = obj.events.createdAt.toOffsetDateTime()
            table.toRow(it, obj)
        }
    }

    override suspend fun insert(objs: List<T>) {
        if (objs.isEmpty()) return
        objs.forEach { this@ExpRepo.insert(it) }
    }

    override suspend fun update(obj: T) {
        table.update(
            where = {
                table.id eq obj.id.uuid
            },
        ) {
            table.toRow(it, obj)
        }
    }

    override suspend fun update(objs: List<T>) {
        if (objs.isEmpty()) return
        objs.forEach { update(it) }
    }

    override suspend fun upsert(obj: T) {
        table.upsert {
            it[table.id] = obj.id.uuid
            it[table.createdAt] = OffsetDateTime.now()
            table.toRow(it, obj)
        }
    }

    override suspend fun upsert(objs: List<T>) {
        if (objs.isEmpty()) return

        objs.forEach {
            upsert(it)
        }
    }

    override suspend fun delete(obj: T) {
        table.deleteWhere {
            table.id eq obj.id.uuid
        }
    }

    override suspend fun delete(objs: List<T>) {
        table.deleteWhere {
            table.id inList objs.map { it.id.uuid }
        }
    }

    override suspend fun deleteId(id: Id<T>) {
        table.deleteWhere {
            table.id eq id.uuid
        }
    }

    override suspend fun deleteIds(ids: List<Id<T>>) {
        if (ids.isEmpty()) return
        table.deleteWhere {
            table.id inList ids.map { it.uuid }
        }
    }

    override suspend fun deleteAll() {
        table.deleteAll()
    }
}
