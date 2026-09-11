package com.urosjarc.ktor_template.core.extend

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.annotations.BlockingExecutor
import java.util.concurrent.Executors

public val Dispatchers.VIRTUAL_THREADS: @BlockingExecutor CoroutineDispatcher
    get() = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
