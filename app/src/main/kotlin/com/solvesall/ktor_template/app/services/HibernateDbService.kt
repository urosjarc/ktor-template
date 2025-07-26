package com.solvesall.ktor_template.app.services

import com.solvesall.ktor_template.app.repos.hibernate.UserHRepo
import com.solvesall.ktor_template.app.repos.hibernate.UserTable
import com.solvesall.ktor_template.core.services.DbService
import com.solvesall.ktor_template.core.types.TimeZoned
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.kotlin.logger
import org.hibernate.SessionFactory
import org.hibernate.StatelessSession
import org.hibernate.Transaction
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.Configuration
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public class HibernateDbService(private val statsSize: Long, config: Configuration) : DbService {
    internal val sessionFactory: SessionFactory

    private val txStatsMutex = Mutex(locked = false)
    private val txStats = mutableMapOf<String, MutableList<DbService.TransactionStat>>()

    init {
        config.addAnnotatedClasses(
            UserTable::class.java
        )

        val serviceRegistry = StandardServiceRegistryBuilder()
            .applySettings(config.properties)
            .build()

        this.sessionFactory = config.buildSessionFactory(serviceRegistry)
    }

    public class HRepos(session: StatelessSession, private val tx: Transaction) : DbService.Transaction {

        public override val repos: DbService.Repos = DbService.Repos(
            user = UserHRepo(session)
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

    override suspend fun <T> transaction(name: String, block: (DbService.Transaction) -> T?): T? {
        val session = sessionFactory.openStatelessSession()
        val tx: Transaction = session.transaction

        val repos = HRepos(session, tx)
        var runTime: Long? = null
        var error = false

        return try {
            tx.begin()
            val result: T?
            runTime = measureTimeMillis { result = block(repos) }
            logger.debug("Transaction finished: ${runTime}ms")
            tx.commit()
            result
        } catch (e: Exception) {
            error = true
            logger.error("Transaction failed", e)
            if (tx.isActive) {
                tx.rollback()
            }
            null
        } finally {
            session.close()
            txStatsMutex.withLock {
                txStats.getOrDefault(name, mutableListOf()).apply {
                    val duration = runTime?.toDuration(DurationUnit.MILLISECONDS)
                    this.add(DbService.TransactionStat(error = error, created = TimeZoned.nowSystem(), duration = duration))
                    if(this.size > statsSize) this.removeFirst()
                }
            }
        }
    }
}
