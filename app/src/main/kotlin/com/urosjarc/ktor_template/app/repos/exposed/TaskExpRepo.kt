package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.app.repos.exposed.generated.TaskTable
import com.urosjarc.ktor_template.core.domain.Task
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.repos.Repo
import com.urosjarc.ktor_template.core.repos.TaskRepo
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll

internal object TaskExpRepo : Repo<Task>, TaskRepo, ExpRepo<Task>(table = TaskTable) {
    override suspend fun selectUserTasks(id: Id<User>): List<Task> =
        TaskTable
            .selectAll()
            .where { TaskTable.userId eq id.uuid }
            .map(TaskTable::toDomain).toList()

    override suspend fun deleteUserTasks(id: Id<User>): Int =
        TaskTable.deleteWhere {
            TaskTable.userId eq id.uuid
        }
}
