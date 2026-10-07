plugins {
    id("buildSrc.common")
    alias(libs.plugins.ksp)
}

kotlin {
    explicitApi()
}

ksp {
    arg("generate.module", "app")
    arg(
        "persistence.core.classes",
        project(":core")
            .layout
            .buildDirectory
            .dir("classes/kotlin/main")
            .get()
            .asFile
            .absolutePath,
    )
}

dependencies {
    ksp(project(":ksp"))
    implementation(project(":core"))
    implementation(libs.bundles.security)
    implementation(libs.bundles.database)
    implementation(libs.bundles.caching)

    runtimeOnly(libs.bundles.security.runtime)
    runtimeOnly(libs.bundles.database.runtime)
}
