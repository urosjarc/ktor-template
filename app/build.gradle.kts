import java.util.Properties

val props = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val projectVersion = props.getProperty("project_version")

plugins {
    id("buildSrc.common")
    id("buildSrc.logging")
    id("buildSrc.db")
    id("buildSrc.liquibase")
    id("buildSrc.security")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":core"))
}

liquibase {
    activities.register("main") {
        val dbUrl = System.getenv("DB_URL")
        val dbUser = System.getenv("DB_USER")
        val dbPassword = System.getenv("DB_PASSWORD")
        val dbDriver = System.getenv("DB_DRIVER")
        val args = mapOf(
            "logLevel" to "info",
            "searchPath" to rootProject.file("data/db").absolutePath.toString(),
            "changelogFile" to "changelog.yml",
            "count" to 1,
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


        logger.lifecycle("")
        logger.lifecycle("Liquibase arguments:")
        args.forEach { (k, v) ->
            logger.lifecycle(" - $k: '$v'")
        }
        logger.lifecycle("")

        this.arguments = args
        runList = "main"
    }
}
