import java.util.Properties
import kotlin.apply

val props = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val tink = props.getProperty("tink")
val ssc = props.getProperty("spring-security-core")
val bj = props.getProperty("bcprov-jdk18on")

plugins {
    kotlin("jvm")
}

dependencies {
	implementation("com.google.crypto.tink:tink:$tink")
	implementation("org.springframework.security:spring-security-core:$ssc")
	runtimeOnly("org.bouncycastle:bcprov-jdk18on:$bj")
}
