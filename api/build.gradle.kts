import java.util.Properties
import kotlin.apply

val props = Properties().apply { load(rootProject.file("project.properties").inputStream()) }
val projectGroup = props.getProperty("project_group")
val projectName = props.getProperty("project_name")

plugins {
    id("buildSrc.common")
    id("buildSrc.server")
    id("buildSrc.logging")
    id("buildSrc.serialization")
    id("buildSrc.openapi")
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":app"))

}

application {
    // Define the Fully Qualified Name for the application main class
    // (Note that Kotlin compiles `App.kt` to a class with FQN `com.example.app.AppKt`.)
    mainClass = "$projectGroup.$projectName.api.MainKt"
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
