package com.urosjarc.ktor_template.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

public interface IdempotencyRepository {
    public suspend fun getOrCreate(execution: IdempotencyExecution): Pair<IdempotencyExecution, Boolean>
    public suspend fun update(execution: IdempotencyExecution)
    public suspend fun findRoot(key: Uuid, scope: String): IdempotencyExecution?
    public suspend fun findById(id: Uuid): IdempotencyExecution?
    public suspend fun children(parentId: Uuid): List<IdempotencyExecution>
}

public class InMemoryIdempotencyRepository : IdempotencyRepository {
    private val mutex = Mutex()
    private val records = mutableMapOf<String, IdempotencyExecution>()

    private fun identity(execution: IdempotencyExecution): String = buildString {
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
        execution: IdempotencyExecution,
    ): Pair<IdempotencyExecution, Boolean> = mutex.withLock {
        val identity = identity(execution)
        records[identity]?.let { return@withLock it to false }
        records[identity] = execution
        execution to true
    }

    override suspend fun update(execution: IdempotencyExecution) {
        mutex.withLock { records[identity(execution)] = execution }
    }

    override suspend fun findRoot(key: Uuid, scope: String): IdempotencyExecution? = mutex.withLock {
        records.values.firstOrNull {
            it.key == key && it.scope == scope && it.parentId == null
        }
    }

    override suspend fun findById(id: Uuid): IdempotencyExecution? = mutex.withLock {
        records.values.firstOrNull { it.id == id }
    }

    override suspend fun children(parentId: Uuid): List<IdempotencyExecution> = mutex.withLock {
        records.values.filter { it.parentId == parentId }.sortedBy { it.childIndex }
    }
}
