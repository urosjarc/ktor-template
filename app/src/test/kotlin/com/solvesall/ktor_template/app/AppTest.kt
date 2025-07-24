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
        var user = random<User>()
        App.services.db.transaction {
            it.repos.user.create(obj = user) ?: return@transaction it.rollback()
            it.repos.user.select()
        }
        user = random<User>()
        App.services.db.transaction {
            it.repos.user.create(obj = user) ?: return@transaction it.rollback()
            it.repos.user.select()
        }
        user = random<User>()
        App.services.db.transaction {
            it.repos.user.create(obj = user) ?: return@transaction it.rollback()
        }
        user = random<User>()
        App.services.db.transaction {
            it.repos.user.select()
        }
    }
}
