package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.core.domain.Task
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.repos.Repo
import com.urosjarc.ktor_template.core.repos.TaskRepo
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll

internal object TaskTable : ExpTable<Task>("tasks") {
    val user_id = reference("user_id", UserTable)
    val name = varchar("name", 255)

    override val paginationDefinition = PaginationDefinition(
        fields = mapOf(
            "id" to PaginationDefinition.field(id),
            "name" to PaginationDefinition.field(name)
        )
    )

    override fun toDomain(row: ResultRow) = Task(
        userId = Id(row[user_id].value),
        name = row[name]
    )

    override fun toRow(tx: UpdateBuilder<Int>, domain: Task) {
        tx[name] = domain.name
        tx[user_id] = domain.userId.value
    }
}

internal object TaskExpRepo : Repo<Task>, TaskRepo, ExpRepo<Task>(table = TaskTable) {
    override suspend fun selectUserTasks(id: Id<User>): List<Task> =
        TaskTable
            .selectAll()
            .where { TaskTable.user_id eq id.value }
            .map(TaskTable::toDomain).toList()

    override suspend fun deleteUserTasks(id: Id<User>): Int =
        TaskTable.deleteWhere {
            TaskTable.user_id eq id.value
        }
}
