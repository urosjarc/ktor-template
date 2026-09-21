package com.urosjarc.ktor_template.core.extend

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private suspend inline fun ext_measureDuration(
    crossinline block: suspend () -> Unit,
): Duration {
    val startedAt = System.nanoTime()
    block()
    return (System.nanoTime() - startedAt).nanoseconds
}

public fun Duration?.ext_percentageOf(total: Duration?): String {
    if (this == null || total == null || total == Duration.ZERO) {
        return "-"
    }

    return "%.2f%%".format(
        inWholeNanoseconds.toDouble() /
                total.inWholeNanoseconds.toDouble() *
                100.0,
    )
}
