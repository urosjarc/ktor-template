plugins {
    id("buildSrc.common")
    alias(libs.plugins.ksp)
}

kotlin {
    explicitApi()
}

dependencies {
    ksp(project(":ksp"))
    implementation(libs.kotlin.reflect)
    implementation(libs.faker)
    implementation(libs.bundles.security)
    runtimeOnly(libs.bundles.security.runtime)
}
