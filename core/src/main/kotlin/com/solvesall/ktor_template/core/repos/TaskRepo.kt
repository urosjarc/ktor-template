package com.solvesall.ktor_template.core.repos

import com.solvesall.ktor_template.core.domain.Task
import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.types.Id

public interface TaskRepo {
    public fun select(userId: Id<User>): Task?
    public fun delete(id: Id<Task>): Task?
    public fun delete(userId: Id<User>): List<Task>
    public fun create(task: Task): Task?
    public fun update(task: Task): Task?
}
