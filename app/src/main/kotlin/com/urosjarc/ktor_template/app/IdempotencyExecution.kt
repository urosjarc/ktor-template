package com.urosjarc.ktor_template.app

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
    public var timing: IdempotencyTiming = IdempotencyTiming(),
) {
    public enum class IdempotencyStatus {
        PROCESSING,
        COMPLETED,
        FAILED,
    }

    public data class IdempotencyTiming(
        public var lookupNanos: Long? = null,
        public var inputSerializationNanos: Long? = null,
        public var executionNanos: Long? = null,
        public var outputSerializationNanos: Long? = null,
        public var persistenceNanos: Long? = null,
    ) {
        public val totalNanos: Long
            get() = listOf(
                lookupNanos,
                inputSerializationNanos,
                executionNanos,
                outputSerializationNanos,
                persistenceNanos,
            ).filterNotNull().sum()
    }
}
