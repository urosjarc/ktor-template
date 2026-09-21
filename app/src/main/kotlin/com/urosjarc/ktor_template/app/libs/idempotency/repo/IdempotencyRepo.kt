package com.urosjarc.ktor_template.app.libs.idempotency.repo

import com.urosjarc.ktor_template.app.libs.idempotency.outputs.Idempotency
import com.urosjarc.ktor_template.core.types.Id

public interface IdempotencyRepo {
    public suspend fun getOrCreate(execution: Idempotency): Pair<Idempotency, Boolean>
    public suspend fun update(domain: Idempotency)
    public suspend fun findRoot(key: Id<Idempotency>, scope: String): Idempotency?
    public suspend fun findById(id: Id<Idempotency>): Idempotency?
    public suspend fun children(parentId: Id<Idempotency>): List<Idempotency>
}
