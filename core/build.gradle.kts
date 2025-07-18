plugins {
    id("buildSrc.common")
    id("buildSrc.logging")
    id("buildSrc.serialization")
    id("buildSrc.security")
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    testImplementation(kotlin("test"))
}
