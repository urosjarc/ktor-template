package com.solvesall.ktor_template.core.repos

import com.solvesall.ktor_template.core.domain.User

public interface UserRepo: Repo<User> {
    public fun selectByUsername(username: String): User?
}
