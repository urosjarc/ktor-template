package com.solvesall.ktor_template.core.repos

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.types.Id

public interface UserRepo {
    public fun select(): List<User>
    public fun select(id: Id<User>): User?
    public fun delete(id: Id<User>): User?
    public fun create(user: User): User?
    public fun update(user: User): User?
}
