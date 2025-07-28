plugins {
    id("org.liquibase.gradle")
}

liquibase {
    activities.register("main") {
        val dbUrl = System.getenv("DB_URL")
        val dbUser = System.getenv("DB_USER")
        val dbPassword = System.getenv("DB_PASSWORD")

        this.arguments = mapOf(
            "logLevel" to "info",
            "changelogFile" to "migrations.yml",
            "url" to dbUrl,
            "username" to dbUser,
            "password" to dbPassword
        )
        runList = "main"
    }
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
