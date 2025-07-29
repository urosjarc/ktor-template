package com.solvesall.ktor_template.app.repos.hibernate

import com.solvesall.ktor_template.core.domain.Task
import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.repos.TaskRepo
import com.solvesall.ktor_template.core.types.Id
import com.solvesall.ktor_template.core.types.TimeZoned
import jakarta.persistence.*
import org.hibernate.StatelessSession
import org.hibernate.annotations.processing.CheckHQL
import java.time.ZonedDateTime
import java.util.*
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import jakarta.persistence.Id as ID

@CheckHQL
@Entity
@Table(name = "tasks")
public data class TaskTable(
    @ID val id: UUID,

    @Column val name: String,
    @Column val createdAt: ZonedDateTime,
    @Column val updatedAt: ZonedDateTime,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: UserTable

) : HTable<Task> {
    override val domain: Task
        get(): Task = Task(
            id = Id(this.id.toKotlinUuid()),
            userId=Id(this.user.id.toKotlinUuid()),
            name=this.name,
            createdAt = TimeZoned(value = this.createdAt),
            updatedAt = TimeZoned(value = this.updatedAt),
        )
}

internal class TaskHRepo(session: StatelessSession) : HRepo<TaskTable, Task>(session, TaskTable::class.java), TaskRepo {

    override fun toRow(obj: Task): TaskTable {
        val user = session.get(UserTable::class.java, obj.userId.value.toJavaUuid())
        return TaskTable(
            id = obj.id.value.toJavaUuid(),
            name = obj.name,
            createdAt = obj.createdAt.value,
            updatedAt = obj.updatedAt.value,
            user = user
        )
    }

    override fun selectUserTasks(id: Id<User>): List<Task> {
        val results = session.createSelectionQuery("FROM TaskTable t WHERE t.user.id = :id", TaskTable::class.java)
            .setParameter("id", id.value.toJavaUuid())
            .list()
        return results.map { it.domain }
    }

    override fun deleteUserTasks(id: Id<User>) {
        session.createMutationQuery("DELETE FROM TaskTable t WHERE t.user.id = :id")
            .setParameter("id", id.value.toJavaUuid())
            .executeUpdate()
    }


}
