interface OpenApiExtension {
	val outputDir: Property<String>
	val inputDir: Property<String>
	val version: Property<String>
	val title: Property<String>
	val description: Property<String>
}

class OpenApi : Plugin<Project> {
	val className = OpenApi::class.simpleName.toString()

	override fun apply(project: Project) {
		val extension = project.extensions.create<OpenApiExtension>(className)

		project.task(className) {
			this.group = "buildSrc"
			this.description = "Fix OpenApi generated documentation."
			doLast {
				val outputDir = File(this.project.projectDir, extension.outputDir.get())
				val inputDir = File(this.project.projectDir, extension.inputDir.get())
				val version = extension.version.get()
				val title = extension.title.get()
				val description = extension.description.get()
				logger.warn("Title:         $title")
				logger.warn("Description:   $description")
				logger.warn("Version:       $version")
				logger.warn("Output dir:    ${outputDir.toRelativeString(project.rootDir)}")
				logger.warn("Input dir:     ${inputDir.toRelativeString(project.rootDir)}")
				inputDir.listFiles { file -> file.isFile && file.extension == "yaml" }?.forEach { file ->
					val content =
						file.readText()
							.replace("version: \".*\"".toRegex(), "version: \"$version\"")
							.replace("'*/*':", "'application/json':")
							.replace("""https://.*""".toRegex(), """http://localhost:8080"""")
					val distFile = File(outputDir, file.name)
					logger.warn("Output file:   ${distFile.toRelativeString(project.rootDir)}")
					distFile.writeText(content)
				}
			}
		}
	}
}

apply<OpenApi>()
