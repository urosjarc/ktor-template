import java.util.Properties

val props = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val ksj = props.getProperty("kotlinx-serialization-json")

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$ksj")
}
