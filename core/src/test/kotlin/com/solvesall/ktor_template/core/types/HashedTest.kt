package com.solvesall.ktor_template.core.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HashedTest {
    @Test
    fun `test hashed value is different from original`() {
        val original = "password123"
        val hashed = original.hashed()

        // Verify that the hashed value is different from the original
        assertNotEquals(original, hashed.toString())
    }

    @Test
    fun `test match returns true for correct password`() {
        val original = "password123"
        val hashed = original.hashed()

        // Verify that match returns true for the original password
        assertTrue(hashed.match(original))
    }

    @Test
    fun `test match returns false for incorrect password`() {
        val original = "password123"
        val incorrect = "password456"
        val hashed = original.hashed()

        // Verify that match returns false for an incorrect password
        assertFalse(hashed.match(incorrect))
    }

    @Test
    fun `test hashing is not deterministic`() {
        val original = "password123"
        val hashed1 = original.hashed()
        val hashed2 = original.hashed()

        // Verify that hashing the same string twice produces different results
        // This is important for security to prevent rainbow table attacks
        assertNotEquals(hashed1.toString(), hashed2.toString())
    }

    @Test
    fun `test toString returns string representation of hashed value`() {
        val original = "password123"
        val hashed = original.hashed()

        // Verify that toString returns a non-empty string
        assert(hashed.toString().isNotEmpty())
    }
}
