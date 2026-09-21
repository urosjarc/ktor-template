package com.urosjarc.ktor_template.app.libs.idempotency.outputs

import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlin.time.Duration
import kotlin.uuid.Uuid

public data class Idempotency(
    public val id: Id<Idempotency>,
    public val key: Uuid,
    public val scope: String,
    public val parentId: Id<Idempotency>?,
    public val childIndex: Long,
    public val useCase: String,
    public val input: String,
    public val inputHash: String,
    public var status: Status,
    public var output: String? = null,
    public var error: String? = null,
    public var time: Time,
    public val createdAt: TimeZoned = TimeZoned.nowUtc(),
    public val updatedAt: TimeZoned = TimeZoned.nowUtc(),
) {
    public enum class Status {
        PROCESSING,
        COMPLETED,
        FAILED,
    }

    public data class Time(
        public var lookup: Duration? = null,
        public var inputSerialization: Duration? = null,
        public var execution: Duration? = null,
        public var outputSerialization: Duration? = null,
        public var persistence: Duration? = null,
        public var total: Duration? = null
    )
}
