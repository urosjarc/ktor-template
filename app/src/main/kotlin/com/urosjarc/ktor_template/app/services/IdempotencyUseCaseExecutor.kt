package com.urosjarc.ktor_template.app.services

import com.urosjarc.ktor_template.app.IdempotencyContextElement
import com.urosjarc.ktor_template.core.usecases.UseCase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

public class IdempotencyUseCaseExecutor(
    private val idempotencyService: IdempotencyService,
) : UseCase.Executor {

    override suspend fun <I : Any, O : Any> execute(
        useCase: UseCase<I, O>,
        input: I,
        block: suspend () -> O,
    ): O {
        val requestContext =
            currentCoroutineContext()[IdempotencyContextElement]
                ?.value
                ?: return block()

        val executionContext = requestContext.copy(
            scope = useCase.name,
        )

        return withContext(
            IdempotencyContextElement(executionContext)
        ) {
            idempotencyService.execute(
                useCase = useCase,
                input = input,
                block = block,
            )
        }
    }
}
