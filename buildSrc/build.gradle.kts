import java.util.Properties
import kotlin.apply

val properties = Properties().apply { load(rootProject.file("../project.properties").inputStream()) }
val kotlin = properties.getProperty("kotlin")
val jdk = properties.getProperty("jdk").toInt()
val kgp = properties.getProperty("kover-gradle-plugin")
val dokka = properties.getProperty("dokka")
val gtlv = properties.getProperty("gradle-test-logger-plugin")
val osb = properties.getProperty("org.springframework.boot")
val isdm = properties.getProperty("io.spring.dependency-management")

plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

kotlin {
    jvmToolchain(jdk)
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin")
    implementation("org.jetbrains.kotlin:kotlin-serialization:$kotlin")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:$kgp")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:$dokka")
    implementation("com.adarshr:gradle-test-logger-plugin:$gtlv")

    implementation("org.jetbrains.kotlin.plugin.spring:org.jetbrains.kotlin.plugin.spring.gradle.plugin:$kotlin")
    implementation("org.jetbrains.kotlin.plugin.jpa:org.jetbrains.kotlin.plugin.jpa.gradle.plugin:$kotlin")
    implementation("io.spring.dependency-management:io.spring.dependency-management.gradle.plugin:$isdm")
    implementation("org.springframework.boot:org.springframework.boot.gradle.plugin:$osb")
}
