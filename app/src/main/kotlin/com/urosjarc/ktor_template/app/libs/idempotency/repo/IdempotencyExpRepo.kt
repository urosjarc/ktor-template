package com.urosjarc.ktor_template.app.libs.idempotency.repo

import com.urosjarc.ktor_template.app.libs.idempotency.outputs.Idempotency
import com.urosjarc.ktor_template.core.extend.ext_toId
import com.urosjarc.ktor_template.core.extend.ext_toTimeZoned
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.r2dbc.insertIgnore
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update


internal class IdempotencyExpRepo : IdempotencyRepo {
    private object Table : UuidTable(name = "system.logs_idempotency") {
        val key = uuid("key")
        val scope = varchar("scope", 500)
        val parentId = uuid("parent_id").nullable()
        val childIndex = long("child_index")
        val useCase = varchar("use_case", 1000)
        val input = text("input")
        val inputHash = varchar("input_hash", 64)
        val status = enumerationByName<Idempotency.Status>("status", 32)
        val output = text("output").nullable()
        val error = text("error").nullable()
        val lookup = duration("lookup").nullable()
        val inputSerialization = duration("input_serialization").nullable()
        val execution = duration("execution").nullable()
        val outputSerialization = duration("output_serialization").nullable()
        val persistence = duration("persistence").nullable()
        val total = duration("total").nullable()
        val createdAt = timestampWithTimeZone("created_at")
        val updatedAt = timestampWithTimeZone("updated_at")

        fun toDomain(row: ResultRow) = Idempotency(
            id = row[id].value.ext_toId(),
            key = row[key],
            scope = row[scope],
            parentId = row[parentId]?.ext_toId(),
            childIndex = row[childIndex],
            useCase = row[useCase],
            input = row[input],
            inputHash = row[inputHash],
            status = row[status],
            output = row[output],
            error = row[error],
            time = Idempotency.Time(
                lookup = row[lookup],
                inputSerialization = row[inputSerialization],
                execution = row[execution],
                outputSerialization = row[outputSerialization],
                persistence = row[persistence],
                total = row[total],
            ),
            createdAt = row[createdAt].ext_toTimeZoned(),
            updatedAt = row[updatedAt].ext_toTimeZoned(),
        )

        fun toRow(tx: UpdateBuilder<*>, domain: Idempotency) {
            tx[id] = EntityID(domain.id.value, Table)
            tx[key] = domain.key
            tx[scope] = domain.scope
            tx[parentId] = domain.parentId?.value
            tx[childIndex] = domain.childIndex
            tx[useCase] = domain.useCase
            tx[input] = domain.input
            tx[inputHash] = domain.inputHash
            tx[status] = domain.status
            tx[output] = domain.output
            tx[error] = domain.error
            tx[lookup] = domain.time.lookup
            tx[inputSerialization] = domain.time.inputSerialization
            tx[execution] = domain.time.execution
            tx[outputSerialization] = domain.time.outputSerialization
            tx[persistence] = domain.time.persistence
            tx[total] = domain.time.total
            tx[createdAt] = domain.createdAt.toOffsetDateTime()
            tx[updatedAt] = domain.updatedAt.toOffsetDateTime()
        }
    }


    override suspend fun getOrCreate(execution: Idempotency): Pair<Idempotency, Boolean> {
        val inserted = Table.insertIgnore { Table.toRow(it, execution) }

        if (inserted.insertedCount == 1) {
            return execution to true
        }

        val idempotency = findByIdentity(execution) ?: error("Idempotency execution ${execution.id} was not found after insert conflict.")

        return idempotency to false
    }

    override suspend fun update(domain: Idempotency) {
        Table.update(where = { Table.id eq domain.id.value }) {
            it[Table.status] = domain.status
            it[Table.output] = domain.output
            it[Table.error] = domain.error
            it[Table.lookup] = domain.time.lookup
            it[Table.inputSerialization] = domain.time.inputSerialization
            it[Table.execution] = domain.time.execution
            it[Table.outputSerialization] = domain.time.outputSerialization
            it[Table.persistence] = domain.time.persistence
            it[Table.total] = domain.time.total
            it[Table.updatedAt] = domain.updatedAt.toOffsetDateTime()
        }
    }

    private suspend fun findByIdentity(execution: Idempotency): Idempotency? =
        Table
            .selectAll()
            .where {
                (Table.key eq execution.key) and
                        (Table.scope eq execution.scope) and
                        (Table.childIndex eq execution.childIndex) and
                        (Table.useCase eq execution.useCase) and
                        if (execution.parentId == null) {
                            Table.parentId.isNull()
                        } else {
                            Table.parentId eq execution.parentId.value
                        }
            }
            .singleOrNull()
            ?.let(Table::toDomain)

    override suspend fun findRoot(key: Id<Idempotency>, scope: String): Idempotency? = Table
        .selectAll()
        .where { (Table.key eq key.value) and (Table.scope eq scope) and Table.parentId.isNull() }
        .singleOrNull()
        ?.let(Table::toDomain)

    override suspend fun findById(id: Id<Idempotency>): Idempotency? = Table
        .selectAll()
        .where { Table.id eq id.value }
        .singleOrNull()
        ?.let(Table::toDomain)

    override suspend fun children(parentId: Id<Idempotency>): List<Idempotency> = Table
        .selectAll()
        .where { Table.parentId eq parentId.value }
        .orderBy(Table.childIndex to SortOrder.ASC)
        .map(Table::toDomain)
        .toList()
}
