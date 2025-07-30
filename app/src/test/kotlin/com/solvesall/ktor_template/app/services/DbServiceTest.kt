package com.solvesall.ktor_template.app.services

import com.solvesall.ktor_template.app.App
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test class for App
 *
 * Tests the Hibernate implementation without Spring dependencies
 */
class DbServiceTest {

    @BeforeTest
    fun setup() = runBlocking {
        App.init(inMemoryDb = true, createDropDb = true)
    }

    @Test
    fun `test seed`() = runBlocking {
        App.seed()
    }

    @Test
    fun `test transaction`() {
        runBlocking {
            App.services.db.transaction("TEST") { tx -> }
            val stats = App.services.db.stats["TEST"]!!
            println(stats)
            assertTrue { stats.size == 1 }
            assertFalse { stats[0].error }
        }
    }
}
