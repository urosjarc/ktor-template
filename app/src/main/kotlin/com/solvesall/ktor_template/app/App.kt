package com.solvesall.ktor_template.app

import com.solvesall.ktor_template.app.services.HibernateDbService
import com.solvesall.ktor_template.core.services.DbService
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.kotlin.logger
import org.hibernate.cfg.Configuration
import java.sql.Connection
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

public object App {

    private var inited: AtomicBoolean = AtomicBoolean(false)
    private var initMutex: Mutex = Mutex(locked = false)

    public object services {
        public lateinit var db: DbService
    }

    public object usecases {

    }

    public suspend fun init(inMemoryDb: Boolean): Unit = this.initMutex.withLock {
        if (this.inited.load()) {
            logger.info("App.init() already finished")
            return
        }
        logger.info("App.init(PID=${Env.PID})")
        delay(10.seconds)

        val config = Configuration()
        config.setProperty("hibernate.connection.username", Env.DB_USER)
        config.setProperty("hibernate.connection.password", Env.DB_PASSWORD)
        config.setProperty("hibernate.connection.isolation", Connection.TRANSACTION_READ_UNCOMMITTED)
        config.setProperty("hibernate.format_sql", true)
        config.setProperty("spring.jpa.properties.hibernate.check_nullability", true)
        config.setProperty("hibernate.generate_statistics", true)
        if (inMemoryDb) {
            config.setProperty("hibernate.hbm2ddl.auto", "create-drop")
            config.setProperty("hibernate.connection.driver_class", "org.h2.Driver")
            config.setProperty("hibernate.connection.url", "jdbc:h2:mem:database.h2;DB_CLOSE_DELAY=-1")
            // config.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
            // config.setProperty("hibernate.connection.url", "jdbc:sqlite:memory:database.sqlite3")
        } else {
            config.setProperty("hibernate.hbm2ddl.auto", "validate")
            config.setProperty("hibernate.connection.driver_class", Env.DB_DRIVER)
            config.setProperty("hibernate.connection.url", Env.DB_URL)
        }

        services.db = HibernateDbService(poolSize = 10, statsSize = 100_000, config = config)

        this.inited.store(true)
        logger.info("App.init() finished")
    }


}
