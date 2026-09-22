package com.urosjarc.ktor_template.app.libs.idempotency

import com.urosjarc.ktor_template.app.UseCaseSerializerCache
import com.urosjarc.ktor_template.app.libs.idempotency.corutines.IdempotencyContext
import com.urosjarc.ktor_template.app.libs.idempotency.corutines.IdempotencyContextElement
import com.urosjarc.ktor_template.app.libs.idempotency.outputs.Idempotency
import com.urosjarc.ktor_template.app.libs.idempotency.outputs.IdempotencySummary
import com.urosjarc.ktor_template.app.libs.idempotency.repo.IdempotencyRepo
import com.urosjarc.ktor_template.core.extend.ext_percentageOf
import com.urosjarc.ktor_template.core.extend.sha256
import com.urosjarc.ktor_template.core.services.JsonService
import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.usecases.UseCase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.coroutines.cancellation.CancellationException
import kotlin.let
import kotlin.time.TimeSource

public class IdempotencyService(
    private val repository: IdempotencyRepo,
) : UseCase.Executor {

    override suspend fun <I : Any, O : Any> execute(
        useCase: UseCase<I, O>,
        input: I,
        block: suspend () -> O,
    ): O {
        val parentContext = currentCoroutineContext()[IdempotencyContextElement]?.value ?: return block()

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
        val context = currentCoroutineContext()[IdempotencyContextElement]?.value ?: error("IdempotencyContext is not installed.")

        val serializers = UseCaseSerializerCache.get(useCase)

        /*
         * Serialize input.
         */
        val inputSerializationMark = TimeSource.Monotonic.markNow()

        val inputJson = JsonService.module.encodeToString(
            serializers.input,
            input,
        )

        val inputSerializationDuration = inputSerializationMark.elapsedNow()

        val inputHash = inputJson.sha256()

        /*
         * Create execution candidate.
         */
        val candidate = Idempotency(
            id = Id(),
            key = context.key,
            scope = context.scope ?: error("Idempotency execution scope is not set."),
            parentId = context.executionId?.let(::Id),
            childIndex = context.nextChildIndex(),
            useCase = useCase.name,
            input = inputJson,
            inputHash = inputHash,
            status = Idempotency.Status.PROCESSING,
            durations = Idempotency.Durations(
                inputSerialization = inputSerializationDuration,
            ),
        )

        /*
         * Persist / find execution.
         */
        val (record, owner) = suspendTransaction {
            repository.getOrCreate(candidate)
        }

        /*
         * Same execution identity must always have the same input.
         */
        if (record.inputHash != inputHash) {
            throw RuntimeException("Idempotency conflict for ${useCase.name}: " + "different input for the same execution identity.")
        }

        /*
         * Existing execution.
         */
        if (!owner) {
            return when (record.status) {
                Idempotency.Status.COMPLETED -> {
                    val output = record.output ?: error(
                        "Completed execution ${record.id} has no output.",
                    )

                    @Suppress("UNCHECKED_CAST") JsonService.module.decodeFromString(
                        deserializer = serializers.output as KSerializer<O>,
                        string = output,
                    )
                }

                Idempotency.Status.PROCESSING -> throw RuntimeException("Execution ${record.id} for " + "${record.useCase} is already processing.")
                Idempotency.Status.FAILED -> throw RuntimeException("Execution ${record.id} for " + "${record.useCase} has previously failed.")
            }
        }

        /*
         * This execution owns the execution record.
         */
        val executionContext = IdempotencyContext(
            key = context.key,
            scope = context.scope,
            executionId = record.id.uuid,
        )

        return try {/*
             * Execute business logic.
             */
            val executionMark = TimeSource.Monotonic.markNow()

            val result = withContext(
                IdempotencyContextElement(executionContext),
            ) {
                block()
            }

            record.durations.execution = executionMark.elapsedNow()

            /*
             * Serialize output.
             */
            val outputSerializationMark = TimeSource.Monotonic.markNow()

            record.output = JsonService.module.encodeToString(
                serializers.output,
                result,
            )

            record.durations.outputSerialization = outputSerializationMark.elapsedNow()

            record.error = null
            record.status = Idempotency.Status.COMPLETED

            result
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            record.status = Idempotency.Status.FAILED
            record.error = "${cause::class.qualifiedName}: ${cause.message}"
            throw cause
        } finally {
            suspendTransaction {
                repository.update(record)
            }
        }
    }

    public suspend fun root(
        key: Id<Idempotency>,
        scope: String,
    ): Idempotency? =
        repository.findRoot(key, scope)
    private suspend fun StringBuilder.appendExecution(
        execution: Idempotency,
        prefix: String,
        isLast: Boolean,
        isRoot: Boolean = false,
    ) {
        val connector = when {
            isRoot -> ""
            isLast -> "`-- "
            else -> "+-- "
        }

        appendLine(
            prefix + connector +
                    "${execution.useCase} [${execution.status}]"
        )

        val detailPrefix =
            if (isRoot) {
                "    "
            } else {
                prefix + if (isLast) "    " else "|   "
            }

        val durations = execution.durations

        val total = execution.events.updatedAt?.let {
            it - execution.events.createdAt
        }

        appendLine(
            "${detailPrefix}input serialization:  " +
                    "${durations.inputSerialization ?: "-"} " +
                    "(${durations.inputSerialization.ext_percentageOf(total)})"
        )

        appendLine(
            "${detailPrefix}execution:            " +
                    "${durations.execution ?: "-"} " +
                    "(${durations.execution.ext_percentageOf(total)})"
        )

        appendLine(
            "${detailPrefix}output serialization: " +
                    "${durations.outputSerialization ?: "-"} " +
                    "(${durations.outputSerialization.ext_percentageOf(total)})"
        )

        appendLine(
            "${detailPrefix}total:                " +
                    "${total ?: "-"} " +
                    "(${total.ext_percentageOf(total)})"
        )

        val children = repository.children(execution.id)

        children.forEachIndexed { index, child ->
            appendExecution(
                execution = child,
                prefix = if (isRoot) {
                    ""
                } else {
                    prefix + if (isLast) "    " else "|   "
                },
                isLast = index == children.lastIndex,
            )
        }
    }

    public suspend fun treeReport(
        rootId: Id<Idempotency>,
    ): String = suspendTransaction {
        val root =
            repository.findById(rootId)
                ?: return@suspendTransaction "Idempotency $rootId not found."

        val total = root.events.updatedAt?.let {
            it - root.events.createdAt
        }

        buildString {
            appendLine("Idempotency execution")
            appendLine("====================")
            appendLine("ID:      ${root.id}")
            appendLine("Key:     ${root.key}")
            appendLine("Scope:   ${root.scope}")
            appendLine("Status:  ${root.status}")
            appendLine("Total:   ${total ?: "-"}")
            appendLine()
            appendLine("Execution tree")
            appendLine("--------------")

            appendExecution(execution = root, prefix = "", isLast = true, isRoot = true)
        }
    }

    public suspend fun tree(rootId: Id<Idempotency>): List<IdempotencySummary> = suspendTransaction {
        val root = repository.findById(rootId) ?: return@suspendTransaction emptyList()

        val result = mutableListOf<IdempotencySummary>()

        suspend fun visit(
            execution: Idempotency,
            depth: Int,
        ) {
            result += IdempotencySummary(
                id = execution.id.uuid,
                useCase = execution.useCase,
                status = execution.status,
                parentId = execution.parentId?.uuid,
                childIndex = execution.childIndex,
                depth = depth,
            )

            for (child in repository.children(execution.id)) {
                visit(child, depth + 1)
            }
        }

        visit(root, 0)

        return@suspendTransaction result
    }
}
