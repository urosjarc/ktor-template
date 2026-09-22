package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.app.libs.pagination.setup.PaginationDefinition
import com.urosjarc.ktor_template.core.domain.Task
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.extend.ext_toId
import com.urosjarc.ktor_template.core.extend.ext_toTimeZoned
import com.urosjarc.ktor_template.core.repos.Repo
import com.urosjarc.ktor_template.core.repos.TaskRepo
import com.urosjarc.ktor_template.core.types.Events
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll

internal object TaskTable : ExpTable<Task>("tasks", Task.serializer()) {
    val user_id = reference("user_id", UserTable)
    val name = varchar("name", 255)

    override val paginationDefinition = PaginationDefinition(
        fields = mapOf(
            "id" to PaginationDefinition.field(id),
            "name" to PaginationDefinition.field(name),
            "createdAt" to PaginationDefinition.field(createdAt),
            "updatedAt" to PaginationDefinition.field(updatedAt)
        )
    )

    override fun toDomain(row: ResultRow) = Task(
        id = row[id].value.ext_toId(),
        userId = row[user_id].value.ext_toId(),
        name = row[name],
        events = Events(
            createdAt = row[createdAt].ext_toTimeZoned(),
            updatedAt = row[updatedAt].ext_toTimeZoned(),
        ),
    )

    override fun toRow(tx: UpdateBuilder<Int>, domain: Task) {
        tx[name] = domain.name
        tx[user_id] = domain.userId.uuid
        tx[id] = EntityID(domain.id.uuid, this)
        tx[createdAt] = domain.events.createdAt.toOffsetDateTime()
    }
}

internal object TaskExpRepo : Repo<Task>, TaskRepo, ExpRepo<Task>(table = TaskTable) {
    override suspend fun selectUserTasks(id: Id<User>): List<Task> =
        TaskTable
            .selectAll()
            .where { TaskTable.user_id eq id.uuid }
            .map(TaskTable::toDomain).toList()

    override suspend fun deleteUserTasks(id: Id<User>): Int =
        TaskTable.deleteWhere {
            TaskTable.user_id eq id.uuid
        }
}
