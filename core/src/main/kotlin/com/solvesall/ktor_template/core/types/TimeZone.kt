package com.solvesall.ktor_template.core.types

import java.time.ZoneId

public class TimeZone {
	public companion object {
		public val SLOVENIA: ZoneId = ZoneId.of("Europe/Ljubljana")
		public val LATVIA: ZoneId = ZoneId.of("Europe/Riga")
		public val UTC: ZoneId = ZoneId.of("UTC")
	}
}
