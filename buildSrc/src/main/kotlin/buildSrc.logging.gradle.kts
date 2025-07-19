import java.util.Properties
import kotlin.apply

val props = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val lak = props.getProperty("log4j-api-kotlin")
val lsi = props.getProperty("log4j-slf4j-impl")
val ls2i = props.getProperty("log4j-slf4j2-impl")

plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-api-kotlin:$lak")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$lsi")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$ls2i")
}
