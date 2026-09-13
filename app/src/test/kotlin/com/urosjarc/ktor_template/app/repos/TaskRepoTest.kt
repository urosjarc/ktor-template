package com.urosjarc.ktor_template.app.repos

import com.urosjarc.ktor_template.app.App
import com.urosjarc.ktor_template.core.domain.Task
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.extend.random
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Test class for TaskRepo
 *
 * Tests the Hibernate implementation without Spring dependencies
 */
class TaskRepoTest {

    @BeforeTest
    fun setup() = runBlocking {
        App.init(inMemoryDb = true, createDropDb = true)
    }

    @Test
    fun `test select operations`() {
        runBlocking {
            var user = random<User>()
            var task = random<Task>().copy(userId = user.id)

            App.services.db.transaction("TASK REPO SELECT") { tx ->
                tx.repos.user.deleteAll()

                // INSERT USER FIRST (REQUIRED FOR FOREIGN KEY)
                tx.repos.user.insert(obj = user)

                // SHOULD BE EMPTY
                assertTrue(tx.repos.task.select().isEmpty())

                // INSERT
                tx.repos.task.insert(obj = task)

                // SHOULD NOT BE EMPTY
                var tasks = tx.repos.task.select()
                assertTrue(tasks.isNotEmpty())
                assertEquals(tasks.map { it.id }, listOf(task.id))

                // SHOULD SELECT TASK BY ID
                assertEquals(tx.repos.task.select(id = task.id)!!.id, task.id)

                // SHOULD SELECT ALL TASKS BY IDS
                assertEquals(tx.repos.task.select(ids = listOf(task.id)).map { it.id }, tasks.map { it.id })
            }
        }
    }

    @Test
    fun `test insert operations`() {
        runBlocking {
            var user = random<User>()
            var task1 = random<Task>().copy(userId = user.id)
            var task2 = random<Task>().copy(userId = user.id)
            var task3 = random<Task>().copy(userId = user.id)

            App.services.db.transaction("TASK REPO INSERT") { tx ->
                tx.repos.user.deleteAll()

                // INSERT USER FIRST (REQUIRED FOR FOREIGN KEY)
                tx.repos.user.insert(obj = user)

                // SHOULD BE EMPTY
                assertTrue(tx.repos.task.select().isEmpty())

                // INSERT SINGLE TASK
                tx.repos.task.insert(obj = task1)
                assertEquals(1, tx.repos.task.select().size)
                assertEquals(task1.id, tx.repos.task.select(id = task1.id)!!.id)

                // INSERT MULTIPLE TASKS
                tx.repos.task.insert(objs = listOf(task2, task3))
                assertEquals(3, tx.repos.task.select().size)
                assertTrue(tx.repos.task.select(ids = listOf(task2.id, task3.id)).map { it.id }.containsAll(listOf(task2.id, task3.id)))
            }
        }
    }

    @Test
    fun `test update operations`() {
        runBlocking {
            var user = random<User>()
            var task = random<Task>().copy(userId = user.id)

            App.services.db.transaction("TASK REPO UPDATE") { tx ->
                tx.repos.user.deleteAll()

                // INSERT USER FIRST (REQUIRED FOR FOREIGN KEY)
                tx.repos.user.insert(obj = user)

                // INSERT TASK
                tx.repos.task.insert(obj = task)

                // UPDATE TASK
                val updatedTask = task.copy(name = "updated_task_name")
                tx.repos.task.update(obj = updatedTask)

                // VERIFY UPDATE
                val retrievedTask = tx.repos.task.select(id = task.id)!!
                assertEquals("updated_task_name", retrievedTask.name)
                assertEquals(task.id, retrievedTask.id)

                // UPDATE MULTIPLE TASKS
                val task2 = random<Task>().copy(userId = user.id)
                tx.repos.task.insert(obj = task2)

                val updatedTask1 = updatedTask.copy(name = "multi_updated_1")
                val updatedTask2 = task2.copy(name = "multi_updated_2")

                tx.repos.task.update(objs = listOf(updatedTask1, updatedTask2))

                // VERIFY MULTIPLE UPDATES
                val retrievedTasks = tx.repos.task.select(ids = listOf(task.id, task2.id))
                assertEquals(2, retrievedTasks.size)
                assertTrue(retrievedTasks.any { it.name == "multi_updated_1" })
                assertTrue(retrievedTasks.any { it.name == "multi_updated_2" })
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
             var task = random<Task>().copy(userId = user.id)

             App.services.db.transaction("TASK REPO UPSERT") { tx ->
                 tx.repos.user.deleteAll()

                 // INSERT USER FIRST (REQUIRED FOR FOREIGN KEY)
                 tx.repos.user.insert(obj = user)

                 // UPSERT NEW TASK (INSERT)
                 tx.repos.task.upsert(obj = task)
                 assertEquals(1, tx.repos.task.select().size)
                 assertEquals(task.id, tx.repos.task.select(id = task.id)!!.id)

                 // UPSERT EXISTING TASK (UPDATE)
                 val updatedTask = task.copy(name = "upserted_task_name")
                 tx.repos.task.upsert(obj = updatedTask)
                 assertEquals(1, tx.repos.task.select().size)
                 assertEquals("upserted_task_name", tx.repos.task.select(id = task.id)!!.name)

                 // UPSERT MULTIPLE TASKS
                 val task2 = random<Task>().copy(userId = user.id)
                 val updatedTask1 = updatedTask.copy(name = "multi_upserted_1")

                 tx.repos.task.upsert(objs = listOf(updatedTask1, task2))
                 assertEquals(2, tx.repos.task.select().size)
                 assertEquals("multi_upserted_1", tx.repos.task.select(id = task.id)!!.name)
                 assertEquals(task2.id, tx.repos.task.select(id = task2.id)!!.id)
             }
         }
     }

    @Test
    fun `test delete operations`() {
        runBlocking {
            var user = random<User>()
            var task1 = random<Task>().copy(userId = user.id)
            var task2 = random<Task>().copy(userId = user.id)
            var task3 = random<Task>().copy(userId = user.id)

            App.services.db.transaction("TASK REPO DELETE") { tx ->
                tx.repos.user.deleteAll()

                // INSERT USER FIRST (REQUIRED FOR FOREIGN KEY)
                tx.repos.user.insert(obj = user)

                // INSERT TASKS
                tx.repos.task.insert(objs = listOf(task1, task2, task3))
                assertEquals(3, tx.repos.task.select().size)

                // DELETE BY OBJECT
                tx.repos.task.delete(obj = task1)
                assertEquals(2, tx.repos.task.select().size)
                assertNull(tx.repos.task.select(id = task1.id))

                // DELETE BY ID
                tx.repos.task.deleteId(id = task2.id)
                assertEquals(1, tx.repos.task.select().size)
                assertNull(tx.repos.task.select(id = task2.id))

                // DELETE MULTIPLE OBJECTS
                var task4 = random<Task>().copy(userId = user.id)
                tx.repos.task.insert(obj = task4)
                assertEquals(2, tx.repos.task.select().size)

                tx.repos.task.delete(objs = listOf(task3, task4))
                assertEquals(0, tx.repos.task.select().size)

                // DELETE MULTIPLE IDS
                var task5 = random<Task>().copy(userId = user.id)
                var task6 = random<Task>().copy(userId = user.id)
                tx.repos.task.insert(objs = listOf(task5, task6))
                assertEquals(2, tx.repos.task.select().size)

                tx.repos.task.deleteIds(ids = listOf(task5.id, task6.id))
                assertEquals(0, tx.repos.task.select().size)
            }
        }
    }

    @Test
    fun `test selectUserTasks operation`() {
        runBlocking {
            var user1 = random<User>()
            var user2 = random<User>()

            var task1 = random<Task>().copy(userId = user1.id, name = "user1_task1")
            var task2 = random<Task>().copy(userId = user1.id, name = "user1_task2")
            var task3 = random<Task>().copy(userId = user2.id, name = "user2_task1")

            App.services.db.transaction("TASK REPO SELECT USER TASKS") { tx ->
                tx.repos.user.deleteAll()

                // INSERT USERS
                tx.repos.user.insert(objs = listOf(user1, user2))

                // INSERT TASKS
                tx.repos.task.insert(objs = listOf(task1, task2, task3))

                // SELECT USER1 TASKS
                val user1Tasks = tx.repos.task.selectUserTasks(id = user1.id)
                assertEquals(2, user1Tasks.size)
                assertTrue(user1Tasks.all { it.userId == user1.id })
                assertTrue(user1Tasks.any { it.name == "user1_task1" })
                assertTrue(user1Tasks.any { it.name == "user1_task2" })

                // SELECT USER2 TASKS
                val user2Tasks = tx.repos.task.selectUserTasks(id = user2.id)
                assertEquals(1, user2Tasks.size)
                assertTrue(user2Tasks.all { it.userId == user2.id })
                assertTrue(user2Tasks.any { it.name == "user2_task1" })

                // SELECT NON-EXISTENT USER TASKS
                var nonExistentUser = random<User>()
                val nonExistentUserTasks = tx.repos.task.selectUserTasks(id = nonExistentUser.id)
                assertTrue(nonExistentUserTasks.isEmpty())
            }
        }
    }

    @Test
    fun `test deleteUserTasks operation`() {
        runBlocking {
            var user1 = random<User>()
            var user2 = random<User>()

            var task1 = random<Task>().copy(userId = user1.id)
            var task2 = random<Task>().copy(userId = user1.id)
            var task3 = random<Task>().copy(userId = user2.id)

            App.services.db.transaction("TASK REPO DELETE USER TASKS") { tx ->
                tx.repos.user.deleteAll()

                // INSERT USERS
                tx.repos.user.insert(objs = listOf(user1, user2))

                // INSERT TASKS
                tx.repos.task.insert(objs = listOf(task1, task2, task3))
                assertEquals(2, tx.repos.task.selectUserTasks(id = user1.id).size)
                assertEquals(1, tx.repos.task.selectUserTasks(id = user2.id).size)

                // DELETE USER1 TASKS
                tx.repos.task.deleteUserTasks(id = user1.id)

                // VERIFY USER1 TASKS DELETED
                assertTrue(tx.repos.task.selectUserTasks(id = user1.id).isEmpty())

                // VERIFY USER2 TASKS STILL EXIST
                assertEquals(1, tx.repos.task.selectUserTasks(id = user2.id).size)

                // DELETE NON-EXISTENT USER TASKS (SHOULD NOT THROW ERROR)
                var nonExistentUser = random<User>()
                tx.repos.task.deleteUserTasks(id = nonExistentUser.id)
            }
        }
    }
}
