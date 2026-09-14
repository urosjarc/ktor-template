package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.repos.Repo
import com.urosjarc.ktor_template.core.repos.UserRepo
import com.urosjarc.ktor_template.core.types.Encrypted
import com.urosjarc.ktor_template.core.types.Hashed
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

internal object UserTable : ExpTable<User>("users") {
    val username = varchar("username", 255)
    val email = binary("email", 255)
    val password = binary("password", 255)
    val type = enumerationByName<User.Type>("type", 255)

    override val paginationDefinition = PaginationDefinition(
        fields = mapOf(
            "id" to PaginationDefinition.field(id),
            "username" to PaginationDefinition.field(username),
            "email" to PaginationDefinition.field(expression = email, paginationType = PaginationType.ENCRYPTED),
            "type" to PaginationDefinition.field(type),
        )
    )

    override fun toDomain(row: ResultRow) = User(
        username = row[username],
        password = Hashed(row[password]),
        email = Encrypted(row[email]),
        type = row[type],
    )

    override fun toRow(tx: UpdateBuilder<Int>, domain: User) {
        tx[password] = domain.password.value
        tx[username] = domain.username
        tx[email] = domain.email.value
        tx[type] = domain.type
    }
}

internal object UserExpRepo : Repo<User>, UserRepo, ExpRepo<User>(table = UserTable) {
    override fun selectByUsername(username: String): User? {
        TODO("Not yet implemented")
    }
}
