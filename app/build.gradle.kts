plugins {
    id("buildSrc.common")
    id("buildSrc.logging")
    id("buildSrc.db")
}
kotlin {
    explicitApi()
}
dependencies {
    implementation(project(":core"))
}
