package com.solvesall.ktor_template.app.repos.hibernate

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.repos.UserRepo
import com.solvesall.ktor_template.core.types.Encrypted
import com.solvesall.ktor_template.core.types.Hashed
import com.solvesall.ktor_template.core.types.Id
import com.solvesall.ktor_template.core.types.TimeZoned
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Id as ID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.time.ZonedDateTime
import kotlin.uuid.Uuid


@Entity
@Table(name = "users")
private data class UserTable(
    @ID val id: Uuid,
    @Column val username: String,
    @Column val password: String,
    @Column val email: String,
    @Column val phone: String,
    @Column val address: String,
    @Column val createdAt: ZonedDateTime,
    @Column val updatedAt: ZonedDateTime
) {
    val domain
        get(): User = User(
            id = Id(this.id),
            username = this.username,
            password = Hashed(this.password.toByteArray()),
            email = Encrypted(this.email.toByteArray()),
            phone = Encrypted(this.phone.toByteArray()),
            address = Encrypted(this.address.toByteArray()),
            createdAt = TimeZoned(value=this.createdAt),
            updatedAt = TimeZoned(value=this.updatedAt),
        )
}

@Repository
private interface UserRepository : JpaRepository<UserTable?, Id<User>?> {
}

@Service
internal class UserHRepo : UserRepo {

    @Autowired
    private lateinit var repo: UserRepository

    override fun create(obj: User): User? {
        val table = UserTable(
            id = obj.id.value,
            username = obj.username,
            password = String(obj.password.value),
            email = String(obj.email.value),
            phone = String(obj.phone.value),
            address = String(obj.address.value),
            createdAt = obj.createdAt.value,
            updatedAt = obj.updatedAt.value
        )
        return repo.save(table).domain

    }

    override fun select(): List<User> = repo.findAll().mapNotNull { it!!.domain }

    override fun select(id: Id<User>): User? = repo.findById(id).orElse(null)?.domain


    override fun update(obj: User): User? {
        TODO("Not yet implemented")
    }

    override fun delete(id: Id<User>): User? {
        TODO("Not yet implemented")
    }


}
