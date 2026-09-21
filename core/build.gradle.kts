plugins {
    id("buildSrc.common")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.faker)
    implementation(libs.bundles.security)
    runtimeOnly(libs.bundles.security.runtime)
}
