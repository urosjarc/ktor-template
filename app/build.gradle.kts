plugins {
    id("buildSrc.common")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(project(":core"))
    implementation(libs.bundles.security)
    runtimeOnly(libs.bundles.security.runtime)
}
