package com.urosjarc.ktor_template.core.repos

import com.urosjarc.ktor_template.core.domain.User

public interface UserRepo: Repo<User> {
    public fun selectByUsername(username: String): User?
}
