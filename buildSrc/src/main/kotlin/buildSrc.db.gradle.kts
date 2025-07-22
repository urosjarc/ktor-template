plugins {
	kotlin("jvm")
	kotlin("plugin.spring")
	kotlin("plugin.jpa")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.5.3")
	implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
	implementation("org.liquibase:liquibase-core:4.33.0")

	runtimeOnly("com.h2database:h2:2.3.232")
	runtimeOnly("org.postgresql:postgresql:42.7.7")

	testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.3")
}
