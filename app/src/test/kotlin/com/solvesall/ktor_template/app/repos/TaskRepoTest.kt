package com.solvesall.ktor_template.app.repos

import com.solvesall.ktor_template.app.App
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Test class for App
 *
 * Tests the Hibernate implementation without Spring dependencies
 */
class TaskRepoTest {

    @BeforeTest
    fun setup() = runBlocking {
        App.init(inMemoryDb = false, createDropDb = true)
        App.seed()
    }

    @Test
    fun `test repos`() {

    }
}
