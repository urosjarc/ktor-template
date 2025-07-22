package com.solvesall.ktor_template.app

import com.solvesall.ktor_template.app.repos.hibernate.UserHRepo
import com.solvesall.ktor_template.app.repos.hibernate.UserTable
import com.solvesall.ktor_template.core.repos.UserRepo
import jakarta.persistence.EntityManagerFactory
import org.apache.logging.log4j.kotlin.logger
import org.hibernate.cfg.Configuration
import org.hibernate.boot.registry.StandardServiceRegistryBuilder


public object App {

    private var inited: Boolean = false
    private lateinit var entityManagerFactory: EntityManagerFactory

    public object repos {
        public lateinit var user: UserRepo
    }

    public object services {

    }

    public object usecases {

    }

    private fun initHibernate() {
        logger.info("Initializing Hibernate...")

        // Create Hibernate configuration
        val configuration = Configuration()

        // Configure database connection
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver")
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
        configuration.setProperty("hibernate.connection.username", "sa")
        configuration.setProperty("hibernate.connection.password", "")

        // Configure Hibernate settings
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop")
        configuration.setProperty("hibernate.format_sql", "true")
        // Make columns non-nullable by default
        configuration.setProperty("spring.jpa.properties.hibernate.check_nullability", "true")

        // Add entity classes
        configuration.addAnnotatedClasses(
            UserTable::class.java
        )

        // Build the SessionFactory (which contains the EntityManagerFactory)
        val serviceRegistry = StandardServiceRegistryBuilder()
            .applySettings(configuration.properties)
            .build()

        val sessionFactory = configuration.buildSessionFactory(serviceRegistry)
        entityManagerFactory = sessionFactory

        logger.info("Hibernate initialized successfully")
    }

    private fun initRepositories() {
        logger.info("Initializing repositories...")
        repos.user = UserHRepo(entityManagerFactory)
        logger.info("Repositories initialized successfully")
    }

    public fun init(){
        if(inited) return else inited = true

        logger.info("App.init()")

        // Initialize Hibernate
        initHibernate()

        // Initialize repositories
        initRepositories()
    }
}
