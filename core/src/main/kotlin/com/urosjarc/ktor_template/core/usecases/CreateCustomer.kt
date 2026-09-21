package com.urosjarc.ktor_template.core.usecases

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

public class CreateCustomer : UseCase<CreateCustomer.Input, CreateCustomer.Output>() {

    @Serializable
    public data class Input(
        public val name: String,
        public val email: String,
    )

    @Serializable
    public data class Output(
        public val customerId: Uuid,
    )

    override suspend fun execute(input: Input): Output =
        Output(customerId = Uuid.random())
}
