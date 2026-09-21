package com.urosjarc.ktor_template.app.libs.idempotency.corutines

import java.util.concurrent.atomic.AtomicLong
import kotlin.uuid.Uuid

public data class IdempotencyContext(
    public val key: Uuid,
    public val scope: String?,
    public val executionId: Uuid?,
    private val childCounter: AtomicLong = AtomicLong(0),
) {
    public fun nextChildIndex(): Long =
        childCounter.getAndIncrement()
}
