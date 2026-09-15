package com.urosjarc.ktor_template.core.types

import com.urosjarc.ktor_template.core.serializers.TimeZonedSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

@JvmInline
@Serializable(TimeZonedSerializer::class)
public value class TimeZoned(public val value: ZonedDateTime) {

	public val zonedDateTime: ZonedDateTime get() = this.value
	public val zone: ZoneId get() = this.value.zone

	public companion object {

		public val SLOVENIA: ZoneId = ZoneId.of("Europe/Ljubljana")
		public val LATVIA: ZoneId = ZoneId.of("Europe/Riga")
		public val UTC: ZoneId = ZoneId.of("UTC")

		public fun nowSystem(): TimeZoned = TimeZoned(ZonedDateTime.now())
		public fun nowSlovenia(): TimeZoned = TimeZoned(ZonedDateTime.now(SLOVENIA))
		public fun nowLatvia(): TimeZoned = TimeZoned(ZonedDateTime.now(LATVIA))
		public fun nowUtc(): TimeZoned = TimeZoned(ZonedDateTime.now(UTC))

		public fun fromEpochMiliseconds(timestamp: Long, atZone: ZoneId): TimeZoned {
			val instant = Instant.ofEpochMilli(timestamp)
			return TimeZoned(ZonedDateTime.ofInstant(instant, atZone))
		}

		public fun fromEpochSeconds(timestamp: Long, atZone: ZoneId): TimeZoned {
			val instant = Instant.ofEpochSecond(timestamp)
			return TimeZoned(ZonedDateTime.ofInstant(instant, atZone))
		}

		public fun parse(data: String): TimeZoned? {
			return try {
				TimeZoned(ZonedDateTime.parse(data))
			} catch (e: DateTimeParseException) {
				return null
			}
		}

		public fun parseLocalDateTime(data: String, atZone: ZoneId): TimeZoned? {
			return try {
				TimeZoned(LocalDateTime.parse(data).atZone(atZone))
			} catch (e: DateTimeParseException) {
				return null
			}
		}
	}

	public fun toZone(zone: ZoneId): TimeZoned {
		return TimeZoned(this.value.withZoneSameInstant(zone))
	}

	public fun toSlovenianTimezone(): TimeZoned {
		return this.toZone(SLOVENIA)
	}

	public fun toUtcTimezone(): TimeZoned {
		return this.toZone(UTC)
	}

	public fun toString(withTimeZone: Boolean): String {
		return if (withTimeZone) {
			this.value.toString()
		} else {
			this.value.toLocalDateTime().toString()
		}
	}

	public fun toOffsetDateTime(): OffsetDateTime {
		return this.value.toOffsetDateTime()
	}

	public override fun toString(): String {
		return this.toString(withTimeZone = true)
	}
}
