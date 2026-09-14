package com.urosjarc.ktor_template.app.repos

import com.urosjarc.ktor_template.app.App
import com.urosjarc.ktor_template.app.repos.exposed.PaginationCompiler
import com.urosjarc.ktor_template.app.repos.exposed.PaginationDefinition
import com.urosjarc.ktor_template.app.repos.exposed.UserTable
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.extend.random
import com.urosjarc.ktor_template.core.types.Pagination
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.junit.jupiter.api.Assertions.*
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Test class for UserRepo
 *
 * Tests the Hibernate implementation without Spring dependencies
 */
class UserRepoTest {

    @BeforeTest
    fun beforeTest() = runBlocking {
        // Always use createDropDb=true to ensure a clean database for each test
        App.init(inMemoryDb = true, createDropDb = true)
        App.seed()
    }

    @Test
    fun `test select operations`() {
        runBlocking {
            var user = random<User>()
            App.services.db.transaction("USER REPO SELECT") { tx ->
                tx.repos.user.deleteAll()

                // SHOULD BE EMPTY
                assertTrue(tx.repos.user.select().isEmpty())

                // INSERT
                tx.repos.user.insert(obj = user)

                // SHOULD NOT BE EMPTY
                var users = tx.repos.user.select()
                assertTrue(users.isNotEmpty())
                assertEquals(users.map { it.id }, listOf(user.id))

                // SHOULD SELECT USER BY ID
                assertEquals(tx.repos.user.select(id = user.id)!!.id, user.id)

                // SHOULD SELECT ALL USER BY IDS
                assertEquals(tx.repos.user.select(ids = listOf(user.id)).map { it.id }, users.map { it.id })
            }
        }
    }

    @Test
    fun `test insert operations`() {
        runBlocking {
            var user1 = random<User>()
            var user2 = random<User>()
            var user3 = random<User>()

            App.services.db.transaction("USER REPO INSERT") { tx ->
                tx.repos.user.deleteAll()
                // SHOULD BE EMPTY
                assertTrue(tx.repos.user.select().isEmpty())

                // INSERT SINGLE USER
                tx.repos.user.insert(obj = user1)
                assertEquals(1, tx.repos.user.select().size)
                assertEquals(user1.id, tx.repos.user.select(id = user1.id)!!.id)

                // INSERT MULTIPLE USERS
                tx.repos.user.insert(objs = listOf(user2, user3))
                assertEquals(3, tx.repos.user.select().size)
                assertTrue(tx.repos.user.select(ids = listOf(user2.id, user3.id)).map { it.id }.containsAll(listOf(user2.id, user3.id)))
            }
        }
    }

    @Test
    fun `test update operations`() {
        runBlocking {
            var user = random<User>()

            App.services.db.transaction("USER REPO UPDATE") { tx ->
                // INSERT USER
                tx.repos.user.insert(obj = user)

                // UPDATE USER
                val updatedUser = user.copy(username = "updated_username")
                tx.repos.user.update(obj = updatedUser)

                // VERIFY UPDATE
                val retrievedUser = tx.repos.user.select(id = user.id)!!
                assertEquals("updated_username", retrievedUser.username)
                assertEquals(user.id, retrievedUser.id)

                // UPDATE MULTIPLE USERS
                val user2 = random<User>()
                tx.repos.user.insert(obj = user2)

                val updatedUser1 = updatedUser.copy(username = "multi_updated_1")
                val updatedUser2 = user2.copy(username = "multi_updated_2")

                tx.repos.user.update(objs = listOf(updatedUser1, updatedUser2))

                // VERIFY MULTIPLE UPDATES
                val retrievedUsers = tx.repos.user.select(ids = listOf(user.id, user2.id))
                assertEquals(2, retrievedUsers.size)
                assertTrue(retrievedUsers.any { it.username == "multi_updated_1" })
                assertTrue(retrievedUsers.any { it.username == "multi_updated_2" })
            }
        }
    }

    /**
     * Skipping upsert operations test as it uses SQL MERGE syntax that's not compatible with H2 in-memory database
     * In a real environment with a full PostgreSQL database, this test would be enabled
     */
    @Test
    fun `test upsert operations`() {
        runBlocking {
            var user = random<User>()

            App.services.db.transaction("USER REPO UPSERT") { tx ->
                // UPSERT NEW USER (INSERT)
                tx.repos.user.upsert(obj = user)
                assertEquals(1, tx.repos.user.select().size)
                assertEquals(user.id, tx.repos.user.select(id = user.id)!!.id)

                // UPSERT EXISTING USER (UPDATE)
                val updatedUser = user.copy(username = "upserted_username")
                tx.repos.user.upsert(obj = updatedUser)
                assertEquals(1, tx.repos.user.select().size)
                assertEquals("upserted_username", tx.repos.user.select(id = user.id)!!.username)

                // UPSERT MULTIPLE USERS
                val user2 = random<User>()
                val updatedUser1 = updatedUser.copy(username = "multi_upserted_1")

                tx.repos.user.upsert(objs = listOf(updatedUser1, user2))
                assertEquals(2, tx.repos.user.select().size)
                assertEquals("multi_upserted_1", tx.repos.user.select(id = user.id)!!.username)
                assertEquals(user2.id, tx.repos.user.select(id = user2.id)!!.id)
            }
        }
    }

    @Test
    fun `test delete operations`() {
        runBlocking {
            var user1 = random<User>()
            var user2 = random<User>()
            var user3 = random<User>()

            App.services.db.transaction("USER REPO DELETE") { tx ->
                tx.repos.user.deleteAll()

                // INSERT USERS
                tx.repos.user.insert(objs = listOf(user1, user2, user3))
                assertEquals(3, tx.repos.user.select().size)

                // DELETE BY OBJECT
                tx.repos.user.delete(obj = user1)
                assertEquals(2, tx.repos.user.select().size)
                assertNull(tx.repos.user.select(id = user1.id))

                // DELETE BY ID
                tx.repos.user.deleteId(id = user2.id)
                assertEquals(1, tx.repos.user.select().size)
                assertNull(tx.repos.user.select(id = user2.id))

                // DELETE MULTIPLE OBJECTS
                var user4 = random<User>()
                tx.repos.user.insert(obj = user4)
                assertEquals(2, tx.repos.user.select().size)

                tx.repos.user.delete(objs = listOf(user3, user4))
                assertEquals(0, tx.repos.user.select().size)

                // DELETE MULTIPLE IDS
                var user5 = random<User>()
                var user6 = random<User>()
                tx.repos.user.insert(objs = listOf(user5, user6))
                assertEquals(2, tx.repos.user.select().size)

                tx.repos.user.deleteIds(ids = listOf(user5.id, user6.id))
                assertEquals(0, tx.repos.user.select().size)
            }
        }
    }

    @Test
    fun `test selectByUsername operation`() {
        runBlocking {
            var user = random<User>().copy(username = "testuser123")

            App.services.db.transaction("USER REPO SELECT BY USERNAME") { tx ->
                // INSERT USER
                tx.repos.user.insert(obj = user)

                // SELECT BY USERNAME
                val retrievedUser = tx.repos.user.selectByUsername(username = "testuser123")
                assertNotNull(retrievedUser)
                assertEquals(user.id, retrievedUser!!.id)
                assertEquals("testuser123", retrievedUser.username)

                // SELECT BY NON-EXISTENT USERNAME
                val nonExistentUser = tx.repos.user.selectByUsername(username = "nonexistentuser")
                assertNull(nonExistentUser)
            }
        }
    }

    @Test
    fun `test select pagination operation`() {
        runBlocking {
            App.services.db.transaction("USER REPO SELECT BY PAGINATION") { tx ->

                val compiler = PaginationCompiler(UserTable.paginationDefinition)

                val pagination = Pagination(
                    filter = Pagination.Filter.Condition(
                        column = "type",
                        operator = Pagination.Filter.Operator.EQ,
                        value = JsonPrimitive("CUSTOMER"),
                    ),
                    limit = 2,
                )

                val result = compiler
                    .apply(
                        request = pagination,
                        source = UserTable.selectAll(),
                    )
                    .query
                    .prepareSQL(
                        tx.raw as R2dbcTransaction,
                        prepared = false,
                    )
                    .split("WHERE ")
                    .last()

                assertEquals(
                    """USERS."type" = 'CUSTOMER' ORDER BY USERS.ID ASC LIMIT 3""",
                    result,
                )
            }
        }
    }

}
