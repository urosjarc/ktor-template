package com.urosjarc.ktor_template.core.repos

import com.urosjarc.ktor_template.core.domain.Task
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.types.Id

public interface TaskRepo: Repo<Task> {
    public fun selectUserTasks(id: Id<User>): List<Task>
    public fun deleteUserTasks(id: Id<User>)
}
