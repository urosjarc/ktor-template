package com.urosjarc.ktor_template.app.services

import com.urosjarc.ktor_template.app.IdempotencyContext
import com.urosjarc.ktor_template.app.IdempotencyContextElement
import com.urosjarc.ktor_template.app.IdempotencyExecution
import com.urosjarc.ktor_template.app.IdempotencyRepository
import com.urosjarc.ktor_template.app.UseCaseSerializerCache
import com.urosjarc.ktor_template.core.extend.sha256
import com.urosjarc.ktor_template.core.services.JsonService
import com.urosjarc.ktor_template.core.usecases.UseCase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

public class IdempotencyService(
    private val repository: IdempotencyRepository,
) : UseCase.Executor {

    public class IdempotencyPending(
        public val executionId: Uuid,
        public val useCase: String,
    ) : RuntimeException(
        "Execution $executionId for $useCase is already processing."
    )

    public class IdempotencyConflict(
        message: String,
    ) : RuntimeException(message)

    public class IdempotencyFailed(
        public val executionId: Uuid,
        public val useCase: String,
    ) : RuntimeException(
        "Execution $executionId for $useCase has previously failed."
    )

    override suspend fun <I : Any, O : Any> execute(
        useCase: UseCase<I, O>,
        input: I,
        block: suspend () -> O,
    ): O {
        val parentContext =
            currentCoroutineContext()[IdempotencyContextElement]
                ?.value
                ?: return block()

        val executionContext = parentContext.copy(
            scope = useCase.name,
        )

        return withContext(
            IdempotencyContextElement(executionContext),
        ) {
            executeIdempotently(
                useCase = useCase,
                input = input,
                block = block,
            )
        }
    }

    private suspend fun <I : Any, O : Any> executeIdempotently(
        useCase: UseCase<I, O>,
        input: I,
        block: suspend () -> O,
    ): O {
        val context =
            currentCoroutineContext()[IdempotencyContextElement]
                ?.value
                ?: error(
                    "IdempotencyContext is not installed."
                )

        val serializers = UseCaseSerializerCache.get(useCase)

        val inputJson =
            JsonService.module.encodeToString(
                serializers.input,
                input,
            )

        val inputHash = inputJson.sha256()

        val candidate = IdempotencyExecution(
            id = Uuid.random(),
            key = context.key,
            scope = context.scope
                ?: error(
                    "Idempotency execution scope is not set."
                ),
            parentId = context.executionId,
            childIndex = context.nextChildIndex(),
            useCase = useCase.name,
            input = inputJson,
            inputHash = inputHash,
            status = IdempotencyExecution.IdempotencyStatus.PROCESSING,
        )

        val (record, owner) =
            repository.getOrCreate(candidate)

        if (record.inputHash != inputHash) {
            throw IdempotencyConflict(
                "Idempotency conflict for ${useCase.name}: " +
                        "different input for the same execution identity.",
            )
        }

        if (!owner) {
            return when (record.status) {
                IdempotencyExecution.IdempotencyStatus.COMPLETED -> {
                    val output =
                        record.output
                            ?: error(
                                "Completed execution ${record.id} " +
                                        "has no output."
                            )

                    @Suppress("UNCHECKED_CAST")
                    JsonService.module.decodeFromString(
                        deserializer = serializers.output as KSerializer<O>,
                        string = output,
                    )
                }

                IdempotencyExecution.IdempotencyStatus.PROCESSING ->
                    throw IdempotencyPending(
                        executionId = record.id,
                        useCase = record.useCase,
                    )

                IdempotencyExecution.IdempotencyStatus.FAILED ->
                    throw IdempotencyFailed(
                        executionId = record.id,
                        useCase = record.useCase,
                    )
            }
        }

        val executionContext = IdempotencyContext(
            key = context.key,
            scope = context.scope,
            executionId = record.id,
        )

        return try {
            val result =
                withContext(
                    IdempotencyContextElement(executionContext),
                ) {
                    block()
                }

            record.status =
                IdempotencyExecution.IdempotencyStatus.COMPLETED

            record.output =
                JsonService.module.encodeToString(
                    serializers.output,
                    result,
                )

            record.error = null

            repository.update(record)

            result
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            record.status =
                IdempotencyExecution.IdempotencyStatus.FAILED

            record.error =
                "${cause::class.qualifiedName}: ${cause.message}"

            repository.update(record)

            throw cause
        }
    }

    public suspend fun root(
        key: Uuid,
        scope: String,
    ): IdempotencyExecution? =
        repository.findRoot(key, scope)

    @Serializable
    public data class ExecutionSummary(
        public val id: Uuid,
        public val useCase: String,
        public val status: IdempotencyExecution.IdempotencyStatus,
        public val parentId: Uuid?,
        public val childIndex: Long,
        public val depth: Int,
    )

    public suspend fun tree(
        rootId: Uuid,
    ): List<ExecutionSummary> {
        val root =
            repository.findById(rootId)
                ?: return emptyList()

        val result = mutableListOf<ExecutionSummary>()

        suspend fun visit(
            execution: IdempotencyExecution,
            depth: Int,
        ) {
            result += ExecutionSummary(
                id = execution.id,
                useCase = execution.useCase,
                status = execution.status,
                parentId = execution.parentId,
                childIndex = execution.childIndex,
                depth = depth,
            )

            for (child in repository.children(execution.id)) {
                visit(child, depth + 1)
            }
        }

        visit(root, 0)

        return result
    }
}
