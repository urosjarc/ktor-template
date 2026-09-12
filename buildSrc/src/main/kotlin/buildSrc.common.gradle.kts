import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.artifacts.VersionCatalogsExtension

val libs = the<VersionCatalogsExtension>().named("libs")

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
    id("com.adarshr.test-logger")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {

    jvmToolchain(libs.findVersion("jvm").get().requiredVersion.toInt())

    compilerOptions.optIn.addAll(
        "kotlin.uuid.ExperimentalUuidApi",
        "kotlinx.coroutines.DelicateCoroutinesApi",
        "kotlin.concurrent.atomics.ExperimentalAtomicApi",
    )
}
repositories {
    mavenCentral()
}

testlogger {
    this.setTheme("mocha")
}

dependencies {
    implementation(libs.findBundle("kotlinx").get())
    implementation(libs.findBundle("logging").get())
    runtimeOnly(libs.findBundle("logging-runtime").get())
    testImplementation(kotlin("test"))
}
configurations.configureEach {
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "ch.qos.logback", module = "logback-core")
}

//TODO:FIX THIS
//group = ""
//rootProject.version = projectVersion
//rootProject.description = projectDescription

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
