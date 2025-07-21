import java.util.Properties
import kotlin.apply

val properties = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val kf = properties.getProperty("kotlin-faker")

plugins {
    id("buildSrc.common")
    id("buildSrc.logging")
    id("buildSrc.serialization")
    id("buildSrc.security")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation("io.github.serpro69:kotlin-faker:$kf")
}
