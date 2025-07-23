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
    fun `test repos`() {
        App.services.db.transaction {
            val user = random<User>()
            val userN = it.userRepo.create(obj = user) ?: return@transaction it.rollback()
            println(userN)
            val users = it.userRepo.select()
            println(users)
        }
    }
}
