package com.urosjarc.ktor_template.core.extend

import com.urosjarc.ktor_template.core.types.TimeZoned
import java.time.OffsetDateTime


public fun OffsetDateTime.ext_toTimeZoned(): TimeZoned = TimeZoned(this.toZonedDateTime())
