plugins {
    id("buildSrc.common")
    id("buildSrc.logging")
    id("buildSrc.db")
    id("buildSrc.db-migration")
}
kotlin {
    explicitApi()
}
dependencies {
    implementation(project(":core"))
}
