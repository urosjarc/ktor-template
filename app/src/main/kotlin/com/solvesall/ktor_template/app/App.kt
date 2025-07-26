package com.solvesall.ktor_template.app

import com.solvesall.ktor_template.app.services.HibernateDbService
import com.solvesall.ktor_template.core.services.DbService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.kotlin.logger
import org.hibernate.cfg.Configuration
import java.sql.Connection
import kotlin.concurrent.atomics.AtomicBoolean

public object App {

    private var inited: AtomicBoolean = AtomicBoolean(false)
    private var initMutex: Mutex = Mutex(locked = false)

    public object services {
        public lateinit var db: DbService
    }

    public object usecases {

    }

    public suspend fun init(inMemoryDb: Boolean = true): Unit = this.initMutex.withLock {
        if (this.inited.load()) {
            logger.info("App.init() already finished")
            return
        }
        logger.info("App.init()")

        val config = Configuration()
        config.setProperty("hibernate.connection.username", "sa")
        config.setProperty("hibernate.connection.password", "")
        config.setProperty("hibernate.hbm2ddl.auto", "create-drop")
        config.setProperty("hibernate.format_sql", true)
        config.setProperty("spring.jpa.properties.hibernate.check_nullability", true)
        config.setProperty("hibernate.connection.isolation", Connection.TRANSACTION_READ_UNCOMMITTED)
        config.setProperty("hibernate.generate_statistics", true)
        if (inMemoryDb) {
            config.setProperty("hibernate.connection.driver_class", "org.h2.Driver")
            config.setProperty("hibernate.connection.url", "jdbc:h2:mem:database.h2;DB_CLOSE_DELAY=-1")
        } else {
            config.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
            config.setProperty("hibernate.connection.url", "jdbc:sqlite:memory:database.sqlite3")
        }

        services.db = HibernateDbService(statsSize = 100_000, config = config)

        this.inited.store(true)
        logger.info("App.init() finished")
    }
}
