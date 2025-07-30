package com.solvesall.ktor_template.app

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
        App.seed()
    }

    @Test
    fun `test created at`() = runBlocking {
        App.services.db.transaction("TEST") { tx ->
            val user = tx.repos.user.select()[0]
            println(user.createdAt)
            println(user.updatedAt)
            tx.repos.user.update(user)
            val user2 = tx.repos.user.select(id=user.id)!!
            println(user2.createdAt)
            println(user2.updatedAt)
        }
    }
}
