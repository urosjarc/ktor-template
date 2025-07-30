package com.solvesall.ktor_template.app.services

import com.solvesall.ktor_template.app.App
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Test class for App
 *
 * Tests the Hibernate implementation without Spring dependencies
 */
class JwtServiceTest {
    val audiences = listOf("aud")
    val payload = "payload"

    @BeforeTest
    fun setup() = runBlocking {
        App.init(inMemoryDb = true, createDropDb = true)
    }

    @Test
    fun expired() {
        val badJwt = App.services.jwt.encode(payload = this.payload, audiences = this.audiences, expiration = 0.seconds)
        assertNull(App.services.jwt.decode(jwt = badJwt, audience = this.audiences.first()))
    }

    @Test
    fun `wrong audience`() {
        val badJwt = App.services.jwt.encode(payload = this.payload, audiences = this.audiences, expiration = 1.seconds)
        assertNull(App.services.jwt.decode(jwt = badJwt, audience = ""))
    }

    @Test
    fun `not validated`() {
        assertNull(App.services.jwt.decode(jwt = "", audience = this.audiences.first()))
    }

    @Test
    fun validated() {
        val jwt = App.services.jwt.encode(payload = this.payload, audiences = this.audiences, expiration = 1.seconds)
        assertNotNull(App.services.jwt.decode(jwt = jwt, audience = this.audiences.first()))
    }
}
