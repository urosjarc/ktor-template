package com.solvesall.ktor_template.app.repos.hibernate

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.repos.UserRepo
import com.solvesall.ktor_template.core.types.Encrypted
import com.solvesall.ktor_template.core.types.Hashed
import com.solvesall.ktor_template.core.types.Id
import com.solvesall.ktor_template.core.types.TimeZoned
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Table
import org.hibernate.annotations.NamedQuery
import org.hibernate.annotations.processing.CheckHQL
import java.time.ZonedDateTime
import java.util.*
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import jakarta.persistence.Id as ID

private const val select_user_by_username = "from UserTable u WHERE u.username = :username"

@CheckHQL
@NamedQuery(name="select_user_by_username", query=select_user_by_username)
@Entity
@Table(name = "users")
public data class UserTable(
    @ID val id: UUID,
    @Column(unique = true) val username: String,
    @Column val password: String,
    @Column(unique = true) val email: String,
    @Column(unique = true) val phone: String,
    @Column val address: String,
    @Column val createdAt: ZonedDateTime,
    @Column val updatedAt: ZonedDateTime
) : HTable<User> {
    override val domain: User
        get(): User = User(
            id = Id(this.id.toKotlinUuid()),
            username = this.username,
            password = Hashed(this.password.toByteArray()),
            email = Encrypted(this.email.toByteArray()),
            phone = Encrypted(this.phone.toByteArray()),
            address = Encrypted(this.address.toByteArray()),
            createdAt = TimeZoned(value = this.createdAt),
            updatedAt = TimeZoned(value = this.updatedAt),
        )
}

internal class UserHRepo(emf: EntityManagerFactory) : HRepo<UserTable, User>(emf, UserTable::class.java), UserRepo {

    override fun toRow(obj: User): UserTable = UserTable(
        id = obj.id.value.toJavaUuid(),
        username = obj.username,
        password = String(obj.password.value),
        email = String(obj.email.value),
        phone = String(obj.phone.value),
        address = String(obj.address.value),
        createdAt = obj.createdAt.value,
        updatedAt = obj.updatedAt.value
    )

    override fun selectByUsername(username: String): User? {
        return this.withTransaction { em ->
            em.createNamedQuery(::select_user_by_username.name, UserTable::class.java)
                .setParameter("username", username)
                .resultList.firstOrNull()?.domain
        }
    }
}
