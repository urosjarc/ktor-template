package com.urosjarc.ktor_template.app.libs.idempotency.repo

import com.urosjarc.ktor_template.app.libs.idempotency.outputs.Idempotency
import com.urosjarc.ktor_template.core.types.Id
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class IdempotencyInMemoryRepo : IdempotencyRepo {
    private val mutex = Mutex()
    private val records = mutableMapOf<String, Idempotency>()

    private fun identity(execution: Idempotency): String = buildString {
        append(execution.key)
        append('|')
        append(execution.scope)
        append('|')
        append(execution.parentId ?: "ROOT")
        append('|')
        append(execution.childIndex)
        append('|')
        append(execution.useCase)
    }

    override suspend fun getOrCreate(
        execution: Idempotency,
    ): Pair<Idempotency, Boolean> = mutex.withLock {
        val identity = identity(execution)
        records[identity]?.let { return@withLock it to false }
        records[identity] = execution
        execution to true
    }

    override suspend fun update(domain: Idempotency) {
        mutex.withLock { records[identity(domain)] = domain }
    }

    override suspend fun findRoot(key: Id<Idempotency>, scope: String): Idempotency? = mutex.withLock {
        records.values.firstOrNull {
            it.key == key.value && it.scope == scope && it.parentId == null
        }
    }

    override suspend fun findById(id: Id<Idempotency>): Idempotency? = mutex.withLock {
        records.values.firstOrNull { it.id.value == id.value }
    }

    override suspend fun children(parentId: Id<Idempotency>): List<Idempotency> = mutex.withLock {
        records.values.filter { it.parentId?.value == parentId.value }.sortedBy { it.childIndex }
    }
}
