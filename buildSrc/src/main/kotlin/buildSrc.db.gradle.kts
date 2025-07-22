plugins {
	kotlin("jvm")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll(
			"-Xjsr305=strict",
		)
	}
}

dependencies {
	annotationProcessor("org.hibernate.orm:hibernate-processor:7.0.6.Final")

	implementation("org.hibernate.orm:hibernate-core:7.0.6.Final")
	implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
	implementation("org.glassfish:jakarta.el:4.0.2")

	runtimeOnly("org.hibernate.orm:hibernate-agroal:7.0.6.Final")
	runtimeOnly("io.agroal:agroal-pool:2.5")
	runtimeOnly("com.h2database:h2:2.3.232")

}
