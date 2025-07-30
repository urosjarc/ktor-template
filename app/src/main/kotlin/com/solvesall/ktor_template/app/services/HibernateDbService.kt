package com.solvesall.ktor_template.app.services

import com.solvesall.ktor_template.app.repos.hibernate.TaskHRepo
import com.solvesall.ktor_template.app.repos.hibernate.TaskTable
import com.solvesall.ktor_template.app.repos.hibernate.UserHRepo
import com.solvesall.ktor_template.app.repos.hibernate.UserTable
import com.solvesall.ktor_template.core.services.DbService
import com.solvesall.ktor_template.core.types.TimeZoned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.kotlin.logger
import org.hibernate.SessionFactory
import org.hibernate.StatelessSession
import org.hibernate.Transaction
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.Configuration
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.Duration
import kotlin.time.measureTime

public class HibernateDbService(private val statsSize: Long, poolSize: Int, config: Configuration) : DbService {
    private val txDispatcher = Dispatchers.IO.limitedParallelism(parallelism = poolSize)
    internal val sessionFactory: SessionFactory

    private val txStatsMutex = Mutex(locked = false)
    private val txStats = mutableMapOf<String, MutableList<DbService.TransactionStat>>()
    private var txStatsIndex = AtomicLong(0)

    init {
        config.setProperty("hibernate.connection.pool_size", poolSize)

        config.addAnnotatedClasses(
            UserTable::class.java,
            TaskTable::class.java
        )

        val serviceRegistry = StandardServiceRegistryBuilder()
            .applySettings(config.properties)
            .build()

        this.sessionFactory = config.buildSessionFactory(serviceRegistry)
    }

    public class HRepos(session: StatelessSession, private val tx: Transaction) : DbService.Transaction {

        public override val repos: DbService.Repos = DbService.Repos(
            user = UserHRepo(session),
            task = TaskHRepo(session)
        )

        override fun commit() {
            tx.commit()
        }

        override fun rollback() {
            tx.rollback()
        }

        override fun timeout(duration: Duration) {
            tx.timeout = duration.inWholeSeconds.toInt()
        }
    }

    override val stats: Map<String, List<DbService.TransactionStat>> get() = txStats.toMap()

    override suspend fun <T> transaction(name: String, block: (DbService.Transaction) -> T): T = withContext(this.txDispatcher) {
        val session = sessionFactory.openStatelessSession()
        val tx: Transaction = session.transaction

        val repos = HRepos(session, tx)
        var runTime: Duration? = null
        var error = false
        val txIndex = txStatsIndex.addAndFetch(1)

        return@withContext try {
            tx.begin()
            val result: T
            runTime = measureTime { result = block(repos) }
            tx.commit()
            result
        } catch (e: Exception) {
            error = true
            logger.error("Transaction failed", e)
            if (tx.isActive) {
                tx.rollback()
            }
            throw e
        } finally {
            session.close()
            txStatsMutex.withLock {
                txStats[name] = txStats.getOrDefault(name, mutableListOf()).apply {
                    this.add(DbService.TransactionStat(index = txIndex, error = error, created = TimeZoned.nowSystem(), duration = runTime))
                    if (this.size > statsSize) this.removeFirst()
                }
            }
        }
    }

}
