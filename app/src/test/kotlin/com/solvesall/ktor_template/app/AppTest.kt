package com.solvesall.ktor_template.app

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.extend.random
import com.solvesall.ktor_template.core.types.Encrypted
import com.solvesall.ktor_template.core.types.Id
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.logger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Test class for App
 *
 * Tests the Hibernate implementation without Spring dependencies
 */
class AppTest {

    @BeforeTest
    fun setup() = runBlocking {
        App.init(inMemoryDb = false, createDropDb = true)
        App.seed()
    }

    @Test
    fun `test repos`() {

    }
}
