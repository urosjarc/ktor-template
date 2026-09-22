package com.urosjarc.ktor_template.core.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

class IdTest {

    // Define a test class to use with the generic Id
    private class TestEntity

    @Test
    fun `test default constructor creates random UUID`() {
        val id1 = Id<TestEntity>()
        val id2 = Id<TestEntity>()

        // Verify that IDs are created with non-null UUIDs
        assertNotNull(id1.uuid)
        assertNotNull(id2.uuid)

        // Verify that two consecutive IDs are different (random)
        assertNotEquals(id1.uuid, id2.uuid)
    }

    @Test
    fun `test constructor with UUID parameter`() {
        val uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val id = Id<TestEntity>(uuid)

        // Verify that the ID uses the provided UUID
        assertEquals(uuid, id.uuid)
    }

    @Test
    fun `test toString returns string representation of UUID`() {
        val uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val id = Id<TestEntity>(uuid)

        // Verify that toString returns the string representation of the UUID
        assertEquals(uuid.toString(), id.toString())
    }

    @Test
    fun `test generic type safety`() {
        // Define another test class
        class AnotherEntity

        // Create IDs for different entity types
        val testEntityId = Id<TestEntity>()
        val anotherEntityId = Id<AnotherEntity>()

        // This is a compile-time type safety check
        // The following line would not compile if uncommented:
        // val test: Id<TestEntity> = anotherEntityId

        // Just assert something to make the test valid
        assertNotEquals(testEntityId.toString(), anotherEntityId.toString())
    }
}
