plugins {
    id("buildSrc.common")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.bundles.security)
    implementation(libs.bundles.database)
    implementation(libs.bundles.caching)

    runtimeOnly(libs.bundles.security.runtime)
    runtimeOnly(libs.bundles.database.runtime)
}
