package com.urosjarc.ktor_template.app.libs.idempotency.outputs

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
public data class IdempotencySummary(
    public val id: Uuid,
    public val useCase: String,
    public val status: Idempotency.Status,
    public val parentId: Uuid?,
    public val childIndex: Long,
    public val depth: Int,
)
