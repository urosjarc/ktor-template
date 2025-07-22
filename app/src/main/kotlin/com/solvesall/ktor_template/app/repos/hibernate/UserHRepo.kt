package com.solvesall.ktor_template.app.repos.hibernate

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.repos.UserRepo
import com.solvesall.ktor_template.core.types.Encrypted
import com.solvesall.ktor_template.core.types.Hashed
import com.solvesall.ktor_template.core.types.Id
import com.solvesall.ktor_template.core.types.TimeZoned
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.EntityTransaction
import jakarta.persistence.Table
import jakarta.persistence.Id as ID
import org.apache.logging.log4j.kotlin.logger
import org.hibernate.annotations.NamedQuery
import org.hibernate.annotations.processing.CheckHQL
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

@CheckHQL
@NamedQuery(name = "findBooksByTitle", query = "from UserTable where username LIKE 'test'")
@Entity
@Table(name = "users")
private data class UserTable(
    @ID val id: UUID,
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
            id = Id(this.id.toKotlinUuid()),
            username = this.username,
            password = Hashed(this.password.toByteArray()),
            email = Encrypted(this.email.toByteArray()),
            phone = Encrypted(this.phone.toByteArray()),
            address = Encrypted(this.address.toByteArray()),
            createdAt = TimeZoned(value = this.createdAt),
            updatedAt = TimeZoned(value = this.updatedAt),
        )

    companion object {
        fun row(obj: User): UserTable = UserTable(
            id = obj.id.value.toJavaUuid(),
            username = obj.username,
            password = String(obj.password.value),
            email = String(obj.email.value),
            phone = String(obj.phone.value),
            address = String(obj.address.value),
            createdAt = obj.createdAt.value,
            updatedAt = obj.updatedAt.value
        )
    }
}

public class UserHRepo(private val emf: EntityManagerFactory) : UserRepo {
    private val logger = logger()

    private inline fun <T> withTransaction(crossinline block: (EntityManager) -> T): T? {
        val em = emf.createEntityManager()
        val tx: EntityTransaction = em.transaction

        return try {
            tx.begin()
            val result = block(em)
            tx.commit()
            result
        } catch (e: Exception) {
            logger.error("Transaction failed", e)
            if (tx.isActive) {
                tx.rollback()
            }
            null
        } finally {
            em.close()
        }
    }

    override fun create(obj: User): User? {
        logger.info("Creating user: ${obj.username}")
        return withTransaction { em ->
            val userTable = UserTable.row(obj)
            em.persist(userTable)
            userTable.domain
        }
    }

    override fun select(): List<User> {
        logger.info("Selecting all users")
        return withTransaction { em ->
            em.createQuery("SELECT u FROM UserTable u", UserTable::class.java)
                .resultList
                .map { it.domain }
        } ?: emptyList()
    }

    override fun select(id: Id<User>): User? {
        logger.info("Selecting user by id: ${id.value}")
        return withTransaction { em ->
            em.find(UserTable::class.java, id.value.toJavaUuid())?.domain
        }
    }

    override fun update(obj: User): User? {
        logger.info("Updating user: ${obj.username}")
        return withTransaction { em ->
            val userTable = UserTable.row(obj)
            em.merge(userTable).domain
        }
    }

    override fun delete(id: Id<User>): User? {
        logger.info("Deleting user by id: ${id.value}")
        return withTransaction { em ->
            val userTable = em.find(UserTable::class.java, id.value.toJavaUuid())
            if (userTable != null) {
                em.remove(userTable)
                userTable.domain
            } else {
                null
            }
        }
    }
}
