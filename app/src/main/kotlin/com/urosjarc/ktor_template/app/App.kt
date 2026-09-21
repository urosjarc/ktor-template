package com.urosjarc.ktor_template.app

import com.urosjarc.ktor_template.app.services.IdempotencyService
import com.urosjarc.ktor_template.app.services.ExposedDbService
import com.urosjarc.ktor_template.app.services.TinkeyJwtService
import com.urosjarc.ktor_template.core.domain.Task
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.extend.random
import com.urosjarc.ktor_template.core.services.DbService
import com.urosjarc.ktor_template.core.services.JsonService
import com.urosjarc.ktor_template.core.services.JwtService
import com.urosjarc.ktor_template.core.usecases.CreateCustomer
import com.urosjarc.ktor_template.core.usecases.CreateOrder
import com.urosjarc.ktor_template.core.usecases.SendEmail
import com.urosjarc.ktor_template.core.usecases.UseCase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.uuid.Uuid

public object App {

    private var inited: AtomicBoolean = AtomicBoolean(false)
    private var initMutex: Mutex = Mutex(locked = false)

    public object services {
        public lateinit var json: JsonService
        public lateinit var idempotency: IdempotencyService
        public lateinit var useCaseExecutor: UseCase.Executor
        public lateinit var db: DbService
        public lateinit var jwt: JwtService
    }

    public object usecases {
        public lateinit var createCustomer: CreateCustomer
        public lateinit var sendEmail: SendEmail
        public lateinit var createOrder: CreateOrder
    }

    public suspend fun init(inMemoryDb: Boolean, createDropDb: Boolean): Unit = this.initMutex.withLock {
        if (this.inited.load()) {
            logger.info("App.init() already finished")
            return
        }
        logger.info("App.init(PID=${Env.PID})")

        // SERVICES
        services.db = ExposedDbService(
            databaseDialect = PostgreSQLDialect(),
            url = "r2dbc:${Env.DB_TYPE}://${Env.DB_USER}:${Env.DB_PASSWORD}@${Env.DB_DOMAIN}:${Env.DB_PORT}/${Env.DB_NAME}",
            statsSize = 1_000_000,
            auditThreadDbMap = mapOf(
                "userId" to "app.user_id",
                "requestId" to "app.request_id",
            )
        )
        services.json = JsonService
        services.jwt = TinkeyJwtService(privateKeyset = Env.KEYSET_JWT)
        services.idempotency = IdempotencyService(InMemoryIdempotencyRepository())
        // SERVICES

        // USECASES
        usecases.createCustomer = CreateCustomer()
        usecases.sendEmail = SendEmail()
        usecases.createOrder = CreateOrder(createCustomer = usecases.createCustomer, sendEmail = usecases.sendEmail)
        // USECASES

        this.inited.store(true)
        logger.info("App.init() finished")
    }

    public suspend fun seed() {

        val users = mutableListOf<User>()
        val tasks = mutableListOf<Task>()

        repeat(10) {
            val user = random<User>()
            users.add(user)
            repeat(10) {
                val task = random<Task>().copy(userId = user.id)
                tasks.add(task)
            }
        }

        services.db.transaction("CREATE") { tx ->
            tx.repos.user.createTable()
            tx.repos.task.createTable()

            tx.repos.task.deleteAll()
            tx.repos.user.deleteAll()

            tx.repos.user.insert(users)
            tx.repos.task.insert(tasks)
        }

    }

    public suspend fun <I : Any, O : Any> execute(useCase: UseCase<I, O>, input: I, key: Uuid, scope: String): O =
        withContext(
            IdempotencyContextElement(
                IdempotencyContext(
                    key = key,
                    scope = scope,
                    executionId = null,
                ),
            )
        ) {
            withContext(UseCase.Element(services.useCaseExecutor)) {
                useCase(input)
            }
        }
}
