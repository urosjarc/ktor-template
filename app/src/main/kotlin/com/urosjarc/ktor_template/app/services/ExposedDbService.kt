package com.urosjarc.ktor_template.app.services

import com.urosjarc.ktor_template.app.repos.exposed.TaskExpRepo
import com.urosjarc.ktor_template.app.repos.exposed.UserExpRepo
import com.urosjarc.ktor_template.core.services.DbService
import com.urosjarc.ktor_template.core.types.TimeZoned
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.kotlin.logger
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime
import kotlin.time.toJavaDuration

internal class ExposedDbService(databaseDialect: DatabaseDialect, url: String, private val statsSize: Long, private val auditThreadDbMap: Map<String, String>) : DbService {
    private val connectionFactory = ConnectionFactories.get(url)

    private val poolConfiguration = ConnectionPoolConfiguration.builder(connectionFactory)
        .initialSize(5)
        .maxSize(20)
        .maxIdleTime(30.minutes.toJavaDuration())
        .build()

    private val pool = ConnectionPool(poolConfiguration)

    private val database = R2dbcDatabase.connect(
        connectionFactory = pool,
        databaseConfig = R2dbcDatabaseConfig.Builder().apply {
            explicitDialect = databaseDialect
        }
    )

    private val txStatsMutex = Mutex(locked = false)
    private val txStats = mutableMapOf<String, MutableList<DbService.TransactionStat>>()
    private var txStatsIndex = AtomicLong(0)


    private class ExposedRepos(private val tx: R2dbcTransaction) : DbService.Transaction {
        override val raw: Any = tx

        private var isCompleted = false

        override val repos: DbService.Repos = DbService.Repos(
            user = UserExpRepo,
            task = TaskExpRepo,
        )

        override suspend fun commit() {
            if (!isCompleted) {
                tx.commit()
                isCompleted = true
            }
        }

        override suspend fun rollback() {
            if (!isCompleted) {
                tx.rollback()
                isCompleted = true
            }
        }

    }

    override val stats: Map<String, List<DbService.TransactionStat>> get() = txStats.toMap()

    override suspend fun <T> transaction(name: String, block: suspend (DbService.Transaction) -> T): T {
        logger.info("Transaction $name started")
        var runTime: Duration? = null
        var error = false
        val txIndex = txStatsIndex.addAndFetch(1)


        val data = suspendTransaction(db = this.database) {

            auditThreadDbMap.forEach { (threadContextKey, dbConfigKey) ->
                exec(
                    stmt = "SELECT set_config('$dbConfigKey', ?, true)",
                    args = listOf(TextColumnType() to (ThreadContext.get(threadContextKey) ?: "")),
                )
            }

            val repos = ExposedRepos(tx = this) // Pass the transaction or repos wrapper here

            try {
                val result: T
                runTime = measureTime {
                    result = block(repos)
                }
                result
            } catch (e: Exception) {
                error = true
                throw e
            } finally {
                txStatsMutex.withLock {
                    txStats.getOrPut(name) { mutableListOf() }.apply {
                        add(
                            DbService.TransactionStat(
                                index = txIndex,
                                error = error,
                                created = TimeZoned.nowSystem(),
                                duration = runTime
                            )
                        )
                        if (size > statsSize) removeFirst()
                    }
                }
            }
        }

        logger.info("Transaction $name finished")
        return data
    }

}
