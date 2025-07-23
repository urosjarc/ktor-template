package com.solvesall.ktor_template.app

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.extend.random
import kotlin.test.*

/**
 * Test class for App
 *
 * Tests the Hibernate implementation without Spring dependencies
 */
class AppTest {

    @BeforeTest
    fun setup() {
        // Initialize the application
        App.init()
    }

    @Test
    fun `test CRUD operations with Hibernate`() {
        // Create a test user
        val testUser = random<User>()

        // Create the user
        val createdUser = App.repos.user.create(testUser)
        assertNotNull(createdUser, "User should be created successfully")
        assertEquals(testUser.username, createdUser.username, "Username should match")

        val users = App.repos.user.selectByUsername(testUser.username)
        println(users)

        val users1 = App.repos.user.select()
        println(users1)

    }
}
