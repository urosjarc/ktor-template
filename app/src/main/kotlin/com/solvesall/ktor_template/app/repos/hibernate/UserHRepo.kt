package com.solvesall.ktor_template.app.repos.hibernate

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.repos.UserRepo
import com.solvesall.ktor_template.core.types.Encrypted
import com.solvesall.ktor_template.core.types.Hashed
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
@Table(name = "users")
public data class UserTable(
    @ID val id: UUID,
    @Column(unique = true) val username: String,
    @Column val password: String,
    @Column(unique = true) val email: String,
    @Column(unique = true) val phone: String,
    @Column val address: String,
    @Column @Enumerated(EnumType.STRING) val type: User.Type,
    @Column val createdAt: ZonedDateTime,
    @Column val updatedAt: ZonedDateTime,

    @OneToMany
    val tasks: List<TaskTable> = emptyList(),

) : HTable<User> {
    override val domain: User
        get(): User = User(
            id = Id(this.id.toKotlinUuid()),
            username = this.username,
            password = Hashed(this.password.toByteArray()),
            email = Encrypted(this.email.toByteArray()),
            phone = Encrypted(this.phone.toByteArray()),
            address = Encrypted(this.address.toByteArray()),
            type = this.type,
            createdAt = TimeZoned(value = this.createdAt),
            updatedAt = TimeZoned(value = this.updatedAt),
        )
}

internal class UserHRepo(session: StatelessSession) : HRepo<UserTable, User>(session, UserTable::class.java), UserRepo {

    override fun toRow(obj: User): UserTable = UserTable(
        id = obj.id.value.toJavaUuid(),
        username = obj.username,
        password = String(obj.password.value),
        email = String(obj.email.value),
        phone = String(obj.phone.value),
        address = String(obj.address.value),
        type = obj.type,
        createdAt = obj.createdAt.value,
        updatedAt = obj.updatedAt.value
    )

    override fun selectByUsername(username: String): User? =
        session.createSelectionQuery("from UserTable u WHERE u.username = :username", UserTable::class.java)
            .setParameter("username", username)
            .resultList.firstOrNull()?.domain
}
