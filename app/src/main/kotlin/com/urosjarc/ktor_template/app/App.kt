package com.urosjarc.ktor_template.app

import com.urosjarc.ktor_template.app.services.TinkeyJwtService
import com.urosjarc.ktor_template.core.domain.Task
import com.urosjarc.ktor_template.core.domain.User
import com.urosjarc.ktor_template.core.extend.random
import com.urosjarc.ktor_template.core.services.DbService
import com.urosjarc.ktor_template.core.services.JsonService
import com.urosjarc.ktor_template.core.services.JwtService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.logging.log4j.kotlin.logger
import kotlin.concurrent.atomics.AtomicBoolean

public object App {

    private var inited: AtomicBoolean = AtomicBoolean(false)
    private var initMutex: Mutex = Mutex(locked = false)

    public object services {
        public lateinit var db: DbService
        public lateinit var json: JsonService
        public lateinit var jwt: JwtService
    }

    public object usecases {

    }

    public suspend fun init(inMemoryDb: Boolean, createDropDb: Boolean): Unit = this.initMutex.withLock {
        if (this.inited.load()) {
            logger.info("App.init() already finished")
            return
        }
        logger.info("App.init(PID=${Env.PID})")

        // SERVICES
//        services.db = initDbService(inMemoryDb = inMemoryDb, createDropDb = createDropDb)
        services.json = JsonService()
        services.jwt = TinkeyJwtService(privateKeyset = Env.KEYSET_JWT)
        // SERVICES

        // USECASES
        // ...
        // USECASES

        this.inited.store(true)
        logger.info("App.init() finished")
    }

//    public fun initDbService(inMemoryDb: Boolean, createDropDb: Boolean): DbService {
//        val config = Configuration()
//        config.setProperty("hibernate.connection.username", Env.DB_USER)
//        config.setProperty("hibernate.connection.password", Env.DB_PASSWORD)
//        config.setProperty("hibernate.connection.isolation", Connection.TRANSACTION_READ_UNCOMMITTED)
//        config.setProperty("hibernate.format_sql", false)
//        config.setProperty("spring.jpa.properties.hibernate.check_nullability", true)
//        config.setProperty("hibernate.generate_statistics", true)
//        config.setProperty("hibernate.hbm2ddl.auto", if (createDropDb) "create-drop" else "validate")
//        if (inMemoryDb) {
//            config.setProperty("hibernate.connection.driver_class", "org.h2.Driver")
//            config.setProperty("hibernate.connection.url", "jdbc:h2:mem:database.h2;DB_CLOSE_DELAY=-1")
//            // config.setProperty("hibernate.connection.driver_class", "org.sqlite.JDBC")
//            // config.setProperty("hibernate.connection.url", "jdbc:sqlite:memory:database.sqlite3")
//        } else {
//            config.setProperty("hibernate.connection.driver_class", Env.DB_DRIVER)
//            config.setProperty("hibernate.connection.url", Env.DB_URL)
//        }
//
//        return HibernateDbService(poolSize = 10, statsSize = 100_000, config = config)
//    }

    public suspend fun seed() {
        services.db.transaction("SEED") { tx ->
            val users = mutableListOf<User>()
            repeat(10) {

                val user = random<User>()
                users.add(user)
                tx.repos.user.insert(obj = user)

                repeat(5) {
                    val task = random<Task>().copy(userId = user.id)
                    tx.repos.task.insert(obj = task)
                }
            }

            tx.repos.task.deleteUserTasks(users[0].id)
            tx.repos.task.deleteUserTasks(users[1].id)
        }

    }


}
