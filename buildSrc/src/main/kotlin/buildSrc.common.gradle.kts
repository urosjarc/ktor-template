import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.util.Properties

val props = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val projectVersion = props.getProperty("project_version")
val projectDescription = props.getProperty("project_description")
val projectGroup = props.getProperty("project_group")
val jdk = props.getProperty("jdk").toInt()

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.adarshr.test-logger")
    id("org.jetbrains.kotlinx.kover")
}
kotlin {
    jvmToolchain(jdk)
    compilerOptions.optIn.addAll(
        "kotlin.uuid.ExperimentalUuidApi",
        "kotlin.io.encoding.ExperimentalEncodingApi"
    )
}
repositories {
    mavenCentral()
}
testlogger {
    this.setTheme("mocha")
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}


group = projectGroup
rootProject.version = projectVersion
rootProject.description = projectDescription

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
