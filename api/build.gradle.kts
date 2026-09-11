plugins {
    id("buildSrc.common")
    id("buildSrc.openapi")
    application
}
application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "com.urosjarc.ktor_template.api.MainKt" //TODO: Make this authomatic!
}

configure<BuildSrc_openapi_gradle.OpenApiExtension> {
    this.outputDir.set("src/main/resources/openapi")
    this.inputDir.set("src/main/resources/openapi")
    this.version.set(rootProject.version.toString())
    this.title.set(rootProject.name)
    this.description.set(rootProject.description)
}

tasks.register("Release") {
    this.group = "buildSrc"
    this.description = "Build release"
    dependsOn("OpenApi")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":app"))
    implementation(libs.bundles.server)
}
