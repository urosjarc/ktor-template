package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.app.repos.exposed.generated.UserTable
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.repos.Repo
import com.urosjarc.ktor_template.core.repos.UserRepo

internal object UserExpRepo : Repo<User>, UserRepo, ExpRepo<User>(table = UserTable) {
    override fun selectByUsername(username: String): User? {
        TODO("Not yet implemented")
    }
}
