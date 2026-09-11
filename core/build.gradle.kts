plugins {
    id("buildSrc.common")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.faker)
    implementation(libs.bundles.security)
    runtimeOnly(libs.bundles.security.runtime)
}
