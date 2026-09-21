package com.urosjarc.ktor_template.core.usecases

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext


public abstract class UseCase<I : Any, O : Any> {

    public interface Executor {
        public suspend fun <I : Any, O : Any> execute(useCase: UseCase<I, O>, input: I, block: suspend () -> O): O
    }

    public class Element(public val executor: Executor) : AbstractCoroutineContextElement(Key) {
        public companion object Key : CoroutineContext.Key<Element>
    }

    public open val name: String get() = this::class.qualifiedName ?: this::class.simpleName ?: error("Unable to determine use-case name.")

    public suspend operator fun invoke(input: I): O {
        val executor = currentCoroutineContext()[Element]?.executor ?: error("UseCaseExecutor is not installed in the coroutine context.")
        return executor.execute(this, input) { execute(input) }
    }

    protected abstract suspend fun execute(input: I): O
}
