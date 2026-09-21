package com.urosjarc.ktor_template.app

import kotlin.coroutines.CoroutineContext

public class IdempotencyContextElement(
    public val value: IdempotencyContext,
) : CoroutineContext.Element {

    public companion object Key : CoroutineContext.Key<IdempotencyContextElement>

    override val key: CoroutineContext.Key<*>
        get() = Key
}
