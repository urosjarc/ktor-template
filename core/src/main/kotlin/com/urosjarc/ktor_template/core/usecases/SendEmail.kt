package com.urosjarc.ktor_template.core.usecases

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

public class SendEmail : UseCase<SendEmail.Input, SendEmail.Output>() {

    @Serializable
    public data class Input(
        public val email: String,
        public val message: String,
    )

    @Serializable
    public data class Output(
        public val sent: Boolean,
    )

    override suspend fun execute(input: Input): Output {
        delay(5.seconds)
        return Output(sent = true)
    }
}
