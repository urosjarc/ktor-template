plugins {
    // The Kotlin DSL plugin provides a convenient way to develop convention plugins.
    // Convention plugins are located in `src/main/kotlin`, with the file extension `.gradle.kts`,
    // and are applied in the project's `build.gradle.kts` files as required.
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

dependencies {
    implementation(libs.bundles.buildsrc)
}
