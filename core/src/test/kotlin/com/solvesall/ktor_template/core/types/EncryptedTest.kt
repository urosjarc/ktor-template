package com.solvesall.ktor_template.core.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Ignore

@Ignore("Requires KEYSET_ENCRYPTED environment variable to be set")
class EncryptedTest {

    @Test
    fun `test encrypted value is different from original`() {
        val original = "sensitive data"
        val encrypted = original.encrypted()

        // Verify that the encrypted value is different from the original
        assertNotEquals(original, encrypted.toString())
    }

    @Test
    fun `test decrypt returns original string`() {
        val original = "sensitive data"
        val encrypted = original.encrypted()

        // Verify that decrypting returns the original string
        assertEquals(original, encrypted.decrypt())
    }

    @Test
    fun `test encryption is deterministic`() {
        val original = "sensitive data"
        val encrypted1 = original.encrypted()
        val encrypted2 = original.encrypted()

        // Verify that encrypting the same string twice produces the same result
        assertEquals(encrypted1.toString(), encrypted2.toString())
    }

    @Test
    fun `test different strings produce different encrypted values`() {
        val original1 = "sensitive data 1"
        val original2 = "sensitive data 2"
        val encrypted1 = original1.encrypted()
        val encrypted2 = original2.encrypted()

        // Verify that encrypting different strings produces different results
        assertNotEquals(encrypted1.toString(), encrypted2.toString())
    }

    @Test
    fun `test toString returns string representation of encrypted value`() {
        val original = "sensitive data"
        val encrypted = original.encrypted()

        // Verify that toString returns a non-empty string
        assert(encrypted.toString().isNotEmpty())
    }
}
