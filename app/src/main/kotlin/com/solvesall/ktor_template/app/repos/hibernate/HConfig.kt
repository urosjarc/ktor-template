package com.solvesall.ktor_template.app.repos.hibernate

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.types.encrypted
import com.solvesall.ktor_template.core.types.hashed
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

@Configuration
@EnableJpaRepositories(basePackages = ["org.solvesall.ktor_template.app.repos.hibernate"])
@EnableTransactionManagement
internal class HConfig {

    @Bean
    fun dataSource(): DataSource {
        val dataSource = DriverManagerDataSource()
        dataSource.setDriverClassName("org.h2.Driver")
        dataSource.url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
        dataSource.username = "sa"
        dataSource.password = ""
        return dataSource
    }

    @Bean
    fun entityManagerFactory(): LocalContainerEntityManagerFactoryBean {
        val em = LocalContainerEntityManagerFactoryBean()
        em.dataSource = dataSource()
        em.setPackagesToScan("data")

        val vendorAdapter = HibernateJpaVendorAdapter()
        vendorAdapter.setGenerateDdl(true)
        em.jpaVendorAdapter = vendorAdapter

        val properties = HashMap<String, Any>()
        properties["hibernate.hbm2ddl.auto"] = "create-drop"
        properties["hibernate.dialect"] = "org.hibernate.dialect.H2Dialect"
        properties["hibernate.show_sql"] = "true"
        em.setJpaPropertyMap(properties)

        return em
    }

    @Bean
    fun transactionManager(): PlatformTransactionManager {
        val transactionManager = JpaTransactionManager()
        transactionManager.entityManagerFactory = entityManagerFactory().`object`
        return transactionManager
    }
}


public fun main() {
    val context = AnnotationConfigApplicationContext(HConfig::class.java)
    val personRepository = context.getBean(UserHRepo::class.java)

    // Create and save a new person
    val person = User(
        username = "",
        password = "".hashed(),
        email = "".encrypted(),
        phone = "".encrypted(),
        address = "".encrypted(),
    )
    personRepository.create(person)
    println("Saved person: $person")

    personRepository.select().forEach { println(it) }

    context.close()
}
