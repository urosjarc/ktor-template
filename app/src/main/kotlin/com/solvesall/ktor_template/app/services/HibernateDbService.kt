package com.solvesall.ktor_template.app.services

import com.solvesall.ktor_template.app.repos.hibernate.UserHRepo
import com.solvesall.ktor_template.app.repos.hibernate.UserTable
import com.solvesall.ktor_template.core.services.DbService
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

public class HibernateDbService(config: Configuration) : DbService {
    internal val sessionFactory: SessionFactory
    private val sessionStats = mutableMapOf<String, DbService.TransactionStats>()

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

    override val stats: List<DbService.TransactionStats> get() = this.sessionStats.values.toList()

    override fun <T> transaction(name: String, block: (DbService.Transaction) -> T?): T? {
        val session = sessionFactory.openStatelessSession()
        val tx: Transaction = session.transaction
        val txStats = this.sessionStats.getOrDefault(name, DbService.TransactionStats(name = name))

        val repos = HRepos(session, tx)

        return try {
            tx.begin()
            val result: T?
            val runTime = measureTimeMillis { result = block(repos) }
            logger.debug("Transaction finished: ${runTime}ms")
            tx.commit()
            txStats.durations.add(runTime.toDuration(DurationUnit.MILLISECONDS))
            txStats.completed++
            result
        } catch (e: Exception) {
            txStats.failed++
            logger.error("Transaction failed", e)
            if (tx.isActive) {
                tx.rollback()
            }
            null
        } finally {
            session.close()
        }
    }
}
