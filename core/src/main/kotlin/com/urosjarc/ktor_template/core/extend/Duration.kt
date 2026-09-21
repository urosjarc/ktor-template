package com.urosjarc.ktor_template.core.extend

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private suspend inline fun measureNanos(
    crossinline block: suspend () -> Unit,
): Duration {
    val startedAt = System.nanoTime()
    block()
    return (System.nanoTime() - startedAt).nanoseconds
}
