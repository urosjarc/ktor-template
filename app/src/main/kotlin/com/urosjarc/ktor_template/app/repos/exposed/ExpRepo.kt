package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.core.domain.Domain
import com.urosjarc.ktor_template.core.repos.Repo
import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.Pagination
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.r2dbc.*
import java.time.OffsetDateTime

internal abstract class ExpTable<T>(name: String) : UuidTable(name, uuidVersion = UuidVersion.V7) {
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    abstract fun toDomain(row: ResultRow): T
    abstract fun toRow(tx: UpdateBuilder<Int>, domain: T)
    val paginationCompiler: PaginationCompiler get() = PaginationCompiler(this)
}

internal open class ExpRepo<T : Domain<T>>(val table: ExpTable<T>) : Repo<T> {
    override suspend fun createTable() {
        SchemaUtils.create(table)
    }

    override suspend fun count(): Long = table.selectAll().count()

    override suspend fun select(): List<T> = table.selectAll().map(table::toDomain).toList()

    override suspend fun select(id: Id<T>): T? = table.selectAll().where { table.id eq id.value }.singleOrNull()?.let(table::toDomain)

    override suspend fun select(ids: List<Id<T>>): List<T> {
        if (ids.isEmpty()) return emptyList()
        return table.selectAll().where { table.id inList ids.map { it.value } }.map(table::toDomain).toList()
    }

    override suspend fun select(pagination: Pagination): Pagination.Page<T> {
        val result = PaginationCompiler(table = table).apply(request = pagination, source = table.selectAll())
        return Pagination.Page(
            data = result.page.data.map(table::toDomain),
            nextCursor = result.page.nextCursor,
        )
    }

    override suspend fun insert(obj: T) {
        table.insert {
            it[table.id] = obj.id.value
            it[table.createdAt] = OffsetDateTime.now()
            it[table.updatedAt] = OffsetDateTime.now()
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
                table.id eq obj.id.value
            },
        ) {
            table.toRow(it, obj)
            it[table.updatedAt] = OffsetDateTime.now()
        }
    }

    override suspend fun update(objs: List<T>) {
        if (objs.isEmpty()) return
        objs.forEach { update(it) }
    }

    override suspend fun upsert(obj: T) {
        table.upsert {
            it[table.id] = obj.id.value
            it[table.createdAt] = OffsetDateTime.now()
            it[table.updatedAt] = OffsetDateTime.now()
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
            table.id eq obj.id.value
        }
    }

    override suspend fun delete(objs: List<T>) {
        table.deleteWhere {
            table.id inList objs.map { it.id.value }
        }
    }

    override suspend fun deleteId(id: Id<T>) {
        table.deleteWhere {
            table.id eq id.value
        }
    }

    override suspend fun deleteIds(ids: List<Id<T>>) {
        if (ids.isEmpty()) return
        table.deleteWhere {
            table.id inList ids.map { it.value }
        }
    }

    override suspend fun deleteAll() {
        table.deleteAll()
    }
}
