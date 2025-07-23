package com.solvesall.ktor_template.core.services

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.extend.faker
import com.solvesall.ktor_template.core.types.Id
import com.solvesall.ktor_template.core.types.TimeZoned
import com.solvesall.ktor_template.core.types.encrypted
import com.solvesall.ktor_template.core.types.hashed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class JsonServiceTest {

    private val jsonService = JsonService(prettyPrint = true)

    // Helper function to create a User for testing
    private fun createTestUser(): User {
        return User(
            id = Id(Uuid.parse("00000000-0000-0000-0000-000000000001")),
            username = "testuser",
            password = "password123".hashed(),
            email = "test@example.com".encrypted(),
            phone = "+1234567890".encrypted(),
            address = "123 Test Street".encrypted(),
            createdAt = TimeZoned.nowUtc(),
            updatedAt = TimeZoned.nowUtc()
        )
    }

    @Test
    fun `test encode User to JSON`() {
        // Arrange
        val user = createTestUser()

        // Act
        val json = jsonService.encode(user)

        // Assert
        assertNotNull(json)
        assertTrue(json.contains("\"id\":"))
        assertTrue(json.contains("\"username\": \"testuser\""))
        assertTrue(json.contains("\"password\":"))
        assertTrue(json.contains("\"email\":"))
        assertTrue(json.contains("\"phone\":"))
        assertTrue(json.contains("\"address\":"))
        assertTrue(json.contains("\"createdAt\":"))
        assertTrue(json.contains("\"updatedAt\":"))
    }

    @Test
    fun `test decode JSON to User`() {
        // Arrange
        val originalUser = createTestUser()
        val json = jsonService.encode(originalUser)

        // Act
        val decodedUser = jsonService.decode<User>(json)

        // Assert
        assertNotNull(decodedUser)
        assertEquals(originalUser.id.toString(), decodedUser.id.toString())
        assertEquals(originalUser.username, decodedUser.username)
        assertEquals(originalUser.password.toString(), decodedUser.password.toString())
        assertEquals(originalUser.email.toString(), decodedUser.email.toString())
        assertEquals(originalUser.phone.toString(), decodedUser.phone.toString())
        assertEquals(originalUser.address.toString(), decodedUser.address.toString())
        assertEquals(originalUser.createdAt.toString(), decodedUser.createdAt.toString())
        assertEquals(originalUser.updatedAt.toString(), decodedUser.updatedAt.toString())
    }

    @Test
    fun `test encode and decode User with special characters in username`() {
        // Arrange
        val user = User(
            id = Id(Uuid.parse("00000000-0000-0000-0000-000000000001")),
            username = "Special \"User\" with \\ and / characters!",
            password = "password123".hashed(),
            email = "test@example.com".encrypted(),
            phone = "+1234567890".encrypted(),
            address = "123 Test Street".encrypted(),
            createdAt = TimeZoned.nowUtc(),
            updatedAt = TimeZoned.nowUtc()
        )

        // Act
        val json = jsonService.encode(user)
        val decodedUser = jsonService.decode<User>(json)

        // Assert
        assertEquals(user.username, decodedUser.username)
    }

}
