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
        val totalMark = TimeSource.Monotonic.markNow()

        val context = currentCoroutineContext()[IdempotencyContextElement]?.value ?: error("IdempotencyContext is not installed.")

        val serializers = UseCaseSerializerCache.get(useCase)

        /* Serialize input */
        val inputSerializationMark = TimeSource.Monotonic.markNow()

        val inputJson = JsonService.module.encodeToString(
            serializers.input,
            input,
        )

        val inputSerializationDuration = inputSerializationMark.elapsedNow()

        val inputHash = inputJson.sha256()

        /* Create execution candidate */
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
            time = Idempotency.Time(
                inputSerialization = inputSerializationDuration,
            ),
        )

        /* Idempotency lookup */
        val lookupMark = TimeSource.Monotonic.markNow()

        val (record, owner) = suspendTransaction {
            repository.getOrCreate(candidate)
        }

        record.time.lookup = lookupMark.elapsedNow()

        /* Different input for the same execution identity. */
        if (record.inputHash != inputHash) {
            throw RuntimeException(
                "Idempotency conflict for ${useCase.name}: " + "different input for the same execution identity.",
            )
        }

        /* Existing execution. */
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

                Idempotency.Status.PROCESSING -> throw RuntimeException(
                    "Execution ${record.id} for " + "${record.useCase} is already processing.",
                )

                Idempotency.Status.FAILED -> throw RuntimeException(
                    "Execution ${record.id} for " + "${record.useCase} has previously failed.",
                )
            }
        }

        val executionContext = IdempotencyContext(
            key = context.key,
            scope = context.scope,
            executionId = record.id.value,
        )

        return try {/* Actual business execution */
            val executionMark = TimeSource.Monotonic.markNow()

            val result = withContext(
                IdempotencyContextElement(executionContext),
            ) {
                block()
            }

            record.time.execution = executionMark.elapsedNow()

            /* Serialize output */
            val outputSerializationMark = TimeSource.Monotonic.markNow()

            record.output = JsonService.module.encodeToString(
                serializers.output,
                result,
            )

            record.time.outputSerialization = outputSerializationMark.elapsedNow()

            record.error = null
            record.status = Idempotency.Status.COMPLETED

            /*
             * Measure everything up to the final persistence operation.
             *
             * Persistence itself is measured separately below.
             */
            val persistenceMark = TimeSource.Monotonic.markNow()

            suspendTransaction {
                repository.update(record)
            }

            record.time.persistence = persistenceMark.elapsedNow()
            record.time.total = totalMark.elapsedNow()

            /*
             * Persist the persistence and total timings.
             *
             * This is intentionally a second update because these values
             * cannot be known until the first persistence operation finishes.
             */
            suspendTransaction {
                repository.update(record)
            }

            result
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            record.status = Idempotency.Status.FAILED
            record.error = "${cause::class.qualifiedName}: ${cause.message}"

            record.time.total = totalMark.elapsedNow()

            suspendTransaction {
                repository.update(record)
            }

            throw cause
        }
    }

    public suspend fun root(
        key: Id<Idempotency>,
        scope: String,
    ): Idempotency? = repository.findRoot(key, scope)

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

        appendLine(prefix + connector + "${execution.useCase} [${execution.status}]")
        val detailPrefix = if (isRoot) "    " else prefix + if (isLast) "    " else "|   "
        val time = execution.time
        val total = time.total

        appendLine("${detailPrefix}lookup:               ${time.lookup ?: "-"} (${time.lookup.ext_percentageOf(total)})")
        appendLine("${detailPrefix}input serialization:  ${time.inputSerialization ?: "-"} (${time.inputSerialization.ext_percentageOf(total)})")
        appendLine("${detailPrefix}execution:            ${time.execution ?: "-"} (${time.execution.ext_percentageOf(total)})")
        appendLine("${detailPrefix}output serialization: ${time.outputSerialization ?: "-"} (${time.outputSerialization.ext_percentageOf(total)})")
        appendLine("${detailPrefix}persistence:          ${time.persistence ?: "-"} (${time.persistence.ext_percentageOf(total)})")
        appendLine("${detailPrefix}total:                ${total ?: "-"} (${total.ext_percentageOf(total)})")

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
        val root = repository.findById(rootId) ?: return@suspendTransaction "Idempotency $rootId not found."

        buildString {
            appendLine("Idempotency execution")
            appendLine("====================")
            appendLine("ID:      ${root.id}")
            appendLine("Key:     ${root.key}")
            appendLine("Scope:   ${root.scope}")
            appendLine("Status:  ${root.status}")
            appendLine("Total:   ${root.time.total ?: "-"}")
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
                id = execution.id.value,
                useCase = execution.useCase,
                status = execution.status,
                parentId = execution.parentId?.value,
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
