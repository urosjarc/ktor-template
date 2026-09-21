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
) {
    public enum class IdempotencyStatus {
        PROCESSING,
        COMPLETED,
        FAILED,
    }
}
