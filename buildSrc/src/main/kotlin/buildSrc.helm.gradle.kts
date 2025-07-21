import java.util.Base64

interface HemlExtension {
	val version: Property<String>
}

class Helm : Plugin<Project> {
	val className = Helm::class.simpleName.toString()

	override fun apply(project: Project) {
		val extension = project.extensions.create<HemlExtension>(className)

		project.tasks.register(className) {
			this.group = "buildSrc"
			this.description = "Set Heml version."
			this.doLast {
				val version = extension.version.get()
				val helmFile = File(project.rootDir, "helm")
				val envFile = File(project.rootDir, ".env")

				logger.warn("\nVersion: $version")

				/** CHART */
				helmFile.walk().filter { it.isFile && it.name == "Chart.yaml" }.forEach { file ->
					logger.warn(" - ${file.toRelativeString(base = helmFile)}")
					val fileText = file
						.readText()
						.replace("version: .*".toRegex(), "version: $version")
						.replace("appVersion: .*".toRegex(), "appVersion: $version")

					file.writeText(fileText)
				}

				var current = ""
				val map = mutableMapOf<String, MutableList<Pair<String, String>>>()
				envFile.readLines().filter { it.isNotBlank() }.forEach { line ->
					if (line.trim().startsWith("#")) {
						current = line.trim(' ', '#')
					} else if (line.contains("=")) {
						val info = line.split("=")
						val key = info.first()
						val value = info.last()
						val lst = map.getOrDefault(current, mutableListOf())
						lst.add(Pair(key, value))
						map[current] = lst
					}
				}
				val base64Encoder = Base64.getEncoder()
				map.filter { it.key.endsWith(".yaml") }.forEach {
					val file = File(project.rootDir, it.key)
					logger.warn(" - $file")
					val text = if (it.key.endsWith("secrets.yaml")) {
						it.value.joinToString("\n") { "${it.first}: ${String(base64Encoder.encode(it.second.trim('\'', '"').toByteArray()))}" }
					} else {
						it.value.joinToString("\n") { "${it.first}: ${it.second}" }
					}
					file.writeText(text)
				}
			}
		}
	}
}

apply<Helm>()
