plugins {
    id("org.liquibase.gradle")
}

dependencies {
    liquibaseRuntime("org.liquibase:liquibase-core:4.33.0")
    liquibaseRuntime("org.liquibase:liquibase-groovy-dsl:3.0.2")
    liquibaseRuntime("info.picocli:picocli:4.6.1")

    // JDBC Drivers
    liquibaseRuntime("com.h2database:h2:2.3.232")
    liquibaseRuntime("org.postgresql:postgresql:42.7.7")
    liquibaseRuntime("org.xerial:sqlite-jdbc:3.25.2")
}
