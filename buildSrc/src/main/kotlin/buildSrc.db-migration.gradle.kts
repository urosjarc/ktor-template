import java.util.Properties
import kotlin.apply

val props = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val projectVersion = props.getProperty("project_version")

plugins {
    id("org.liquibase.gradle")
}

liquibase {
    activities.register("main") {
        val dbUrl = System.getenv("DB_URL")
        val dbUser = System.getenv("DB_USER")
        val dbPassword = System.getenv("DB_PASSWORD")
        val dbDriver = System.getenv("DB_DRIVER")

        this.arguments = mapOf(
            "logLevel" to "info",
            "searchPath" to rootProject.file("data/migrations").absolutePath.toString(),
            "changelogFile" to "main.yml",
            "driver" to dbDriver,
            "url" to dbUrl,
            "referenceDriver" to dbDriver,
            "referenceUrl" to dbUrl,
            "referenceUsername" to dbUser,
            "referencePassword" to dbPassword,
            "username" to dbUser,
            "password" to dbPassword,
            "tag" to projectVersion
        )
        println(this.arguments)
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
