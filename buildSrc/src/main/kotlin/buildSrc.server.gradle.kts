import java.util.Properties
import kotlin.apply

val props = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val ktor = props.getProperty("ktor")
val mrp = props.getProperty("micrometer-registry-prometheus")
val ksrl = props.getProperty("ktor-server-rate-limiting")

plugins {
    kotlin("jvm")
}

dependencies {
    implementation("io.micrometer:micrometer-registry-prometheus:$mrp")
    implementation("io.github.flaxoos:ktor-server-rate-limiting:$ksrl")

    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-resources:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-host-common:$ktor")
    implementation("io.ktor:ktor-server-sse:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-compression:$ktor")
    implementation("io.ktor:ktor-server-call-id:$ktor")
    implementation("io.ktor:ktor-server-call-logging:$ktor")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktor")
    implementation("io.ktor:ktor-server-auth:$ktor")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor")
    implementation("io.ktor:ktor-server-csrf:$ktor")
    implementation("io.ktor:ktor-server-cors:$ktor")
    implementation("io.ktor:ktor-server-swagger:$ktor")
    implementation("io.ktor:ktor-server-openapi:$ktor")
}
