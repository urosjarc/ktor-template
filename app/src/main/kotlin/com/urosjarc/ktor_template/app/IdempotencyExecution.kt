package com.urosjarc.ktor_template.app

import kotlin.time.Duration
import kotlin.uuid.Uuid

public data class IdempotencyExecution(
    public val id: Uuid,
    public val key: Uuid,
    public val scope: String,
    public val parentId: Uuid?,
    public val childIndex: Long,
    public val useCase: String,
    public val input: String,
    public val inputHash: String,
    public var status: IdempotencyStatus,
    public var output: String? = null,
    public var error: String? = null,
    public var timing: Time = Time(),
) {
    public enum class IdempotencyStatus {
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
