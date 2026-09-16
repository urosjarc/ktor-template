package com.urosjarc.ktor_template.app.repos.exposed

import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.extend.ext_toId
import com.urosjarc.ktor_template.core.extend.ext_toTimeZoned
import com.urosjarc.ktor_template.core.repos.Repo
import com.urosjarc.ktor_template.core.repos.UserRepo
import com.urosjarc.ktor_template.core.types.Encrypted
import com.urosjarc.ktor_template.core.types.Hashed
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder

internal object UserTable : ExpTable<User>("users", User.serializer()) {
    val username = varchar("username", 255)
    val email = binary("email", 255)
    val password = binary("password", 255)
    val type = enumerationByName<User.Type>("type", 255)

    override val paginationDefinition = PaginationDefinition(
        fields = mapOf(
            "id" to PaginationDefinition.field(id),
            "username" to PaginationDefinition.field(username),
            "email" to PaginationDefinition.field(email, PaginationType.ENCRYPTED),
            "type" to PaginationDefinition.field(type),
            "createdAt" to PaginationDefinition.field(createdAt),
            "updatedAt" to PaginationDefinition.field(updatedAt)
        )
    )

    override fun toDomain(row: ResultRow) = User(
        username = row[username],
        password = Hashed(row[password]),
        email = Encrypted(row[email]),
        type = row[type],
        id = row[id].value.ext_toId(),
        createdAt = row[createdAt].ext_toTimeZoned(),
        updatedAt = row[updatedAt].ext_toTimeZoned(),
    )

    override fun toRow(tx: UpdateBuilder<Int>, domain: User) {
        tx[id] = domain.id.value
        tx[createdAt] = domain.createdAt.toOffsetDateTime()
        tx[updatedAt] = domain.updatedAt.toOffsetDateTime()
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
