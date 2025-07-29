plugins {
	kotlin("jvm")
	kotlin("plugin.jpa")
	kotlin("kapt")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xjsr305=strict",
		)
	}
}
dependencies {
	// CORE
	implementation("org.hibernate.orm:hibernate-core:7.0.6.Final")
	kapt("org.hibernate.orm:hibernate-processor:7.0.6.Final")
	kapt("org.hibernate.validator:hibernate-validator:8.0.1.Final")

	// CONNECTION POOL
	runtimeOnly("org.hibernate.orm:hibernate-agroal:7.0.6.Final")
	runtimeOnly("io.agroal:agroal-pool:2.5")

	// H2 Database
	runtimeOnly("com.h2database:h2:2.3.232")

	// Postgresql
	runtimeOnly("org.postgresql:postgresql:42.7.7")

	// Sqlite Database
	runtimeOnly("org.hibernate.orm:hibernate-community-dialects:7.0.7.Final")
	runtimeOnly("org.xerial:sqlite-jdbc:3.25.2")
}
