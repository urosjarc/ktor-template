package com.urosjarc.ktor_template.core.usecases

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

public class CreateOrder(
    private val createCustomer: CreateCustomer,
    private val sendEmail: SendEmail,
) : UseCase<CreateOrder.Input, CreateOrder.Output>() {

    @Serializable
    public data class Input(
        public val customerName: String,
        public val email: String,
    )

    @Serializable
    public data class Output(
        public val orderId: Uuid,
    )

    override suspend fun execute(input: Input): Output {
        createCustomer(
            CreateCustomer.Input(
                name = input.customerName,
                email = input.email,
            ),
        )

        sendEmail(
            SendEmail.Input(
                email = input.email,
                message = "Your order has been created.",
            ),
        )

        return Output(orderId = Uuid.random())
    }
}
