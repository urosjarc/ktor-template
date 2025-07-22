package com.solvesall.ktor_template.core.types

import com.solvesall.ktor_template.core.types.TimeZoned.Companion.LATVIA
import com.solvesall.ktor_template.core.types.TimeZoned.Companion.SLOVENIA
import com.solvesall.ktor_template.core.types.TimeZoned.Companion.UTC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

class TimeZonedTest {

    @Test
    fun `test nowSystem creates TimeZoned with system timezone`() {
        val timeZoned = TimeZoned.nowSystem()
        assertNotNull(timeZoned)
        assertEquals(ZonedDateTime.now().zone, timeZoned.zone)
    }

    @Test
    fun `test nowSlovenia creates TimeZoned with Slovenia timezone`() {
        val timeZoned = TimeZoned.nowSlovenia()
        assertEquals(SLOVENIA, timeZoned.zone)
    }

    @Test
    fun `test nowLatvia creates TimeZoned with Latvia timezone`() {
        val timeZoned = TimeZoned.nowLatvia()
        assertEquals(LATVIA, timeZoned.zone)
    }

    @Test
    fun `test nowUtc creates TimeZoned with UTC timezone`() {
        val timeZoned = TimeZoned.nowUtc()
        assertEquals(UTC, timeZoned.zone)
    }

    @Test
    fun `test fromEpochMiliseconds creates TimeZoned with correct time and zone`() {
        val timestamp = 1626859200000L // 2021-07-21T12:00:00Z
        val zone = UTC
        val timeZoned = TimeZoned.fromEpochMiliseconds(timestamp, zone)

        assertEquals(zone, timeZoned.zone)
        assertEquals(Instant.ofEpochMilli(timestamp), timeZoned.zonedDateTime.toInstant())
    }

    @Test
    fun `test fromEpochSeconds creates TimeZoned with correct time and zone`() {
        val timestamp = 1626859200L // 2021-07-21T12:00:00Z
        val zone = UTC
        val timeZoned = TimeZoned.fromEpochSeconds(timestamp, zone)

        assertEquals(zone, timeZoned.zone)
        assertEquals(Instant.ofEpochSecond(timestamp), timeZoned.zonedDateTime.toInstant())
    }

    @Test
    fun `test parse valid string returns TimeZoned`() {
        val dateTimeString = "2021-07-21T12:00:00+00:00[UTC]"
        val timeZoned = TimeZoned.parse(dateTimeString)

        assertNotNull(timeZoned)
        assertEquals(ZonedDateTime.parse(dateTimeString), timeZoned.zonedDateTime)
    }

    @Test
    fun `test parse invalid string returns null`() {
        val invalidString = "not-a-date-time"
        val timeZoned = TimeZoned.parse(invalidString)

        assertNull(timeZoned)
    }

    @Test
    fun `test parseLocalDateTime valid string returns TimeZoned`() {
        val dateTimeString = "2021-07-21T12:00:00"
        val zone = UTC
        val timeZoned = TimeZoned.parseLocalDateTime(dateTimeString, zone)

        assertNotNull(timeZoned)
        assertEquals(LocalDateTime.parse(dateTimeString).atZone(zone), timeZoned.zonedDateTime)
    }

    @Test
    fun `test parseLocalDateTime invalid string returns null`() {
        val invalidString = "not-a-date-time"
        val zone = UTC
        val timeZoned = TimeZoned.parseLocalDateTime(invalidString, zone)

        assertNull(timeZoned)
    }

    @Test
    fun `test toZone converts to specified timezone`() {
        val original = TimeZoned.nowUtc()
        val converted = original.toZone(SLOVENIA)

        assertEquals(SLOVENIA, converted.zone)
        assertEquals(original.zonedDateTime.toInstant(), converted.zonedDateTime.toInstant())
    }

    @Test
    fun `test toSlovenianTimezone converts to Slovenia timezone`() {
        val original = TimeZoned.nowUtc()
        val converted = original.toSlovenianTimezone()

        assertEquals(SLOVENIA, converted.zone)
        assertEquals(original.zonedDateTime.toInstant(), converted.zonedDateTime.toInstant())
    }

    @Test
    fun `test toUtcTimezone converts to UTC timezone`() {
        val original = TimeZoned.nowSlovenia()
        val converted = original.toUtcTimezone()

        assertEquals(UTC, converted.zone)
        assertEquals(original.zonedDateTime.toInstant(), converted.zonedDateTime.toInstant())
    }

    @Test
    fun `test toString with timezone includes timezone information`() {
        val dateTime = ZonedDateTime.parse("2021-07-21T12:00:00+00:00[UTC]")
        val timeZoned = TimeZoned(dateTime)

        assertEquals(dateTime.toString(), timeZoned.toString(withTimeZone = true))
    }

    @Test
    fun `test toString without timezone excludes timezone information`() {
        val dateTime = ZonedDateTime.parse("2021-07-21T12:00:00+00:00[UTC]")
        val timeZoned = TimeZoned(dateTime)

        assertEquals(dateTime.toLocalDateTime().toString(), timeZoned.toString(withTimeZone = false))
    }

    @Test
    fun `test toString defaults to including timezone information`() {
        val dateTime = ZonedDateTime.parse("2021-07-21T12:00:00+00:00[UTC]")
        val timeZoned = TimeZoned(dateTime)

        assertEquals(dateTime.toString(), timeZoned.toString())
    }
}
