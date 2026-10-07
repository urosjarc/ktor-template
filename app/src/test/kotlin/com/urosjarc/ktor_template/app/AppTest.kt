package com.urosjarc.ktor_template.app

import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test

class AppTest {

    @BeforeTest
    fun setup() = runBlocking {
        App.init(inMemoryDb = false, createDropDb = true)
    }

    @Test
    fun `test seed`() = runBlocking {
    }

}
