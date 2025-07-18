import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.io.FileInputStream
import java.util.Properties

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.adarshr.test-logger")
    id("org.jetbrains.kotlinx.kover")
}
kotlin {
    jvmToolchain(21)
}
repositories {
    mavenCentral()
}
testlogger {
    this.setTheme("mocha")
}
tasks.dokkaHtml {
    dokkaSourceSets {
        configureEach {
            documentedVisibilities.set(
                setOf(
                    org.jetbrains.dokka.DokkaConfiguration.Visibility.PUBLIC, // Same for both Kotlin and Java
                    org.jetbrains.dokka.DokkaConfiguration.Visibility.PRIVATE, // Same for both Kotlin and Java
                    org.jetbrains.dokka.DokkaConfiguration.Visibility.PROTECTED, // Same for both Kotlin and Java
                    org.jetbrains.dokka.DokkaConfiguration.Visibility.INTERNAL, // Kotlin-specific internal modifier
                    org.jetbrains.dokka.DokkaConfiguration.Visibility.PACKAGE, // Java-specific package-private visibility
                ),
            )
            includeNonPublic.set(true)
            jdkVersion.set(21)
            reportUndocumented.set(true)
            skipEmptyPackages.set(false)
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

val properties = Properties()
val input = FileInputStream(file("${rootDir}/project.properties"))
properties.load(input)
input.close()

group = "com.solvesall"
rootProject.version = properties.getProperty("project_version")
rootProject.description = properties.getProperty("project_description")



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
