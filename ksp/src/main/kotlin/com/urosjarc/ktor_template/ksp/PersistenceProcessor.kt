package com.urosjarc.ktor_template.ksp

import java.io.File

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

class PersistenceProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val processorOptions: Map<String, String>,
) : SymbolProcessor {

    private val generated = mutableSetOf<String>()

    private companion object {
        const val PERSISTENCE_ANNOTATION =
            "com.urosjarc.ktor_template.core.annotations.Persistence"

        const val PUT_ANNOTATION =
            "com.urosjarc.ktor_template.core.annotations.Put"

        const val EVENTS_TYPE =
            "com.urosjarc.ktor_template.core.types.Events"

        const val HASHED_TYPE =
            "com.urosjarc.ktor_template.core.types.Hashed"

        const val ENCRYPTED_TYPE =
            "com.urosjarc.ktor_template.core.types.Encrypted"

        const val ID_TYPE =
            "com.urosjarc.ktor_template.core.types.Id"

        const val GENERATED_PACKAGE =
            "com.urosjarc.ktor_template.app.repos.exposed.generated"
    }

    override fun process(
        resolver: Resolver,
    ): List<KSAnnotated> {
        logger.warn("! PERSISTENCE PROCESS")

        val persistenceClasses = findCoreClasses()
            .mapNotNull { className ->
                resolver.getClassDeclarationByName(
                    resolver.getKSNameFromString(className),
                )
            }
            .filter { it.hasAnnotation(PERSISTENCE_ANNOTATION) }

        logger.warn(
            "Found ${persistenceClasses.size} @Persistence classes",
        )

        persistenceClasses.forEach { domain ->
            val generatedName =
                "${domain.simpleName.asString()}Table"

            if (!generated.add(generatedName)) {
                logger.warn(
                    "Skipping already generated: $generatedName",
                )
                return@forEach
            }

            generate(domain)
        }

        return emptyList()
    }

    private fun findCoreClasses(): List<String> {
        val path = processorOptions["persistence.core.classes"]
            ?: error("Missing KSP option: persistence.core.classes")

        val root = File(path)

        require(root.isDirectory) {
            "Core classes directory does not exist: $root"
        }

        return root
            .walkTopDown()
            .filter { it.isFile }
            .filter { it.extension == "class" }
            .mapNotNull { file ->
                val relative = file
                    .relativeTo(root)
                    .invariantSeparatorsPath

                if ('$' in relative) {
                    null
                } else {
                    relative
                        .removeSuffix(".class")
                        .replace('/', '.')
                }
            }
            .toList()
    }

    private fun KSClassDeclaration.hasAnnotation(
        annotationName: String,
    ): Boolean =
        annotations.any {
            it.annotationType
                .resolve()
                .declaration
                .qualifiedName
                ?.asString() == annotationName
        }

    private fun generate(
        domain: KSClassDeclaration,
    ) {
        logger.warn(
            "Processing: ${domain.qualifiedName?.asString()}"
        )

        val persistence = domain.annotations
            .firstOrNull {
                it.annotationType.resolve()
                    .declaration.qualifiedName?.asString() ==
                        PERSISTENCE_ANNOTATION
            }
            ?: return

        val arguments = persistence.arguments.associate {
            it.name!!.asString() to it.value
        }

        val configuredTable = arguments["table"] as? String

        val table = if (!configuredTable.isNullOrBlank()) {
            configuredTable
        } else {
            domain.simpleName
                .asString()
                .toSnakeCase()
        }

        val properties = domain
            .getAllProperties()
            .filter { it.isConstructorProperty() }
            .filterNot { it.isInfrastructureProperty() }
            .toList()

        val putProperties = properties.filter {
            it.hasAnnotation(PUT_ANNOTATION)
        }

        val domainName = domain.simpleName.asString()
        val generatedName = "${domainName}Table"

        logger.info(
            "Generating $GENERATED_PACKAGE.$generatedName"
        )

        val content = buildString {
            appendHeader(
                domainName = domainName,
                generatedName = generatedName,
                table = table,
            )

            appendColumns(properties)
            appendPagination(properties)

            appendToInsert(
                domainName = domainName,
                properties = properties,
            )

            appendToUpdate(
                domainName = domainName,
                properties = putProperties,
            )

            appendToDomain(
                domainName = domainName,
                properties = properties,
            )

            appendLine("}")
        }

        codeGenerator
            .createNewFile(
                dependencies = Dependencies(
                    aggregating = true,
                ),
                packageName = GENERATED_PACKAGE,
                fileName = generatedName,
            )
            .use { output ->
                output.writer().use { writer ->
                    writer.write(content)
                }
            }
    }

    private fun StringBuilder.appendHeader(
        domainName: String,
        generatedName: String,
        table: String,
    ) {
        appendLine("package $GENERATED_PACKAGE")
        appendLine()

        appendLine(
            "import com.urosjarc.ktor_template.app.libs.pagination.PaginationCompiler"
        )

        appendLine(
            "import com.urosjarc.ktor_template.app.libs.pagination.setup.PaginationDefinition"
        )

        appendLine(
            "import com.urosjarc.ktor_template.app.libs.pagination.setup.PaginationType"
        )

        appendLine(
            "import com.urosjarc.ktor_template.app.repos.exposed.ExpTable"
        )

        appendLine(
            "import com.urosjarc.ktor_template.core.extend.ext_toId"
        )

        appendLine(
            "import com.urosjarc.ktor_template.core.extend.ext_toTimeZoned"
        )

        appendLine(
            "import com.urosjarc.ktor_template.core.types.Encrypted"
        )

        appendLine(
            "import com.urosjarc.ktor_template.core.types.Events"
        )

        appendLine(
            "import com.urosjarc.ktor_template.core.types.Hashed"
        )

        appendLine(
            "import org.jetbrains.exposed.v1.core.ResultRow"
        )

        appendLine(
            "import org.jetbrains.exposed.v1.core.statements.UpdateBuilder"
        )

        appendLine()

        appendLine(
            "import com.urosjarc.ktor_template.core.domain.$domainName"
        )

        appendLine()

        appendLine(
            "internal object $generatedName : ExpTable<$domainName>("
        )

        appendLine("    \"$table\",")
        appendLine("    $domainName.serializer(),")
        appendLine(") {")

        appendLine()
    }

    private fun StringBuilder.appendColumns(
        properties: List<KSPropertyDeclaration>,
    ) {
        properties.forEach { property ->
            val name = property.simpleName.asString()
            val columnName = name.toSnakeCase()
            val type = property.type.resolve()

            appendLine(
                "    val $name = " +
                        "${type.toExposedColumn(columnName, property)}"
            )
        }

        appendLine()
    }

    private fun StringBuilder.appendPagination(
        properties: List<KSPropertyDeclaration>,
    ) {
        appendLine(
            "    override val paginationDefinition = PaginationDefinition("
        )

        appendLine("        fields = mapOf(")

        appendLine(
            "            \"id\" to PaginationDefinition.field(id),"
        )

        properties.forEach { property ->
            val name = property.simpleName.asString()

            if (name == "id") {
                return@forEach
            }

            val paginationType =
                if (property.type.resolve().isEncrypted()) {
                    ", PaginationType.ENCRYPTED"
                } else {
                    ""
                }

            appendLine(
                "            \"$name\" to " +
                        "PaginationDefinition.field($name$paginationType),"
            )
        }

        appendLine(
            "            \"createdAt\" to " +
                    "PaginationDefinition.field(createdAt),"
        )

        appendLine(
            "            \"updatedAt\" to " +
                    "PaginationDefinition.field(updatedAt),"
        )

        appendLine("        ),")
        appendLine("    )")
        appendLine()
    }

    private fun StringBuilder.appendToInsert(
        domainName: String,
        properties: List<KSPropertyDeclaration>,
    ) {
        appendLine("    override fun toInsert(")
        appendLine("        tx: UpdateBuilder<Int>,")
        appendLine("        domain: $domainName,")
        appendLine("    ) {")

        appendLine("        tx[id] = domain.id.uuid")

        appendLine(
            "        tx[createdAt] = " +
                    "domain.events.createdAt.toOffsetDateTime()"
        )

        properties.forEach { property ->
            appendLine(
                "        tx[${property.simpleName.asString()}] = " +
                        property.toWriteExpression()
            )
        }

        appendLine("    }")
        appendLine()
    }

    private fun StringBuilder.appendToUpdate(
        domainName: String,
        properties: List<KSPropertyDeclaration>,
    ) {
        appendLine("    override fun toUpdate(")
        appendLine("        tx: UpdateBuilder<Int>,")
        appendLine("        domain: $domainName,")
        appendLine("    ) {")

        properties.forEach { property ->
            appendLine(
                "        tx[${property.simpleName.asString()}] = " +
                        property.toWriteExpression()
            )
        }

        appendLine("    }")
        appendLine()
    }

    private fun StringBuilder.appendToDomain(
        domainName: String,
        properties: List<KSPropertyDeclaration>,
    ) {
        appendLine("    override fun toDomain(")
        appendLine("        row: ResultRow,")
        appendLine("    ) = $domainName(")

        appendLine(
            "        id = row[id].value.ext_toId(),"
        )

        properties.forEach { property ->
            appendLine(
                "        ${property.simpleName.asString()} = " +
                        property.toReadExpression() + ","
            )
        }

        appendLine("        events = Events(")

        appendLine(
            "            createdAt = " +
                    "row[createdAt].ext_toTimeZoned(),"
        )

        appendLine(
            "            insertedAt = " +
                    "row[insertedAt].ext_toTimeZoned(),"
        )

        appendLine(
            "            updatedAt = " +
                    "row[updatedAt].ext_toTimeZoned(),"
        )

        appendLine("        ),")
        appendLine("    )")
        appendLine()
    }

    private fun KSPropertyDeclaration.toWriteExpression(): String {
        val name = simpleName.asString()
        val type = type.resolve()

        return when {
            type.isHashed() ->
                "domain.$name.value"

            type.isEncrypted() ->
                "domain.$name.value"

            type.isId() ->
                "domain.$name.uuid"

            else ->
                "domain.$name"
        }
    }

    private fun KSPropertyDeclaration.toReadExpression(): String {
        val name = simpleName.asString()
        val type = type.resolve()

        return when {
            type.isHashed() ->
                "Hashed(row[$name])"

            type.isEncrypted() ->
                "Encrypted(row[$name])"

            type.isId() ->
                "row[$name].value.ext_toId()"

            else ->
                "row[$name]"
        }
    }

    private fun KSType.toExposedColumn(
        columnName: String,
        property: KSPropertyDeclaration,
    ): String {
        val qualifiedName =
            declaration.qualifiedName?.asString()
                ?: error(
                    "Unable to resolve type of " +
                            property.simpleName.asString(),
                )

        val classDeclaration =
            declaration as? KSClassDeclaration

        return when {
            qualifiedName == "kotlin.String" ->
                """varchar("$columnName", 255)"""

            qualifiedName == HASHED_TYPE ->
                """binary("$columnName", 255)"""

            qualifiedName == ENCRYPTED_TYPE ->
                """binary("$columnName", 255)"""

            qualifiedName == ID_TYPE ->
                """reference(
    "$columnName",
    ${referencedTableName(this, property)}.id,
)"""

            classDeclaration?.classKind == ClassKind.ENUM_CLASS -> {
                val enumName =
                    classDeclaration.qualifiedName?.asString()
                        ?: classDeclaration.simpleName.asString()

                """enumerationByName<$enumName>(
    "$columnName",
    255,
)"""
            }

            else -> error(
                "Unsupported persistence type '$qualifiedName' " +
                        "for property '${property.simpleName.asString()}'",
            )
        }
    }

    private fun referencedTableName(
        type: KSType,
        property: KSPropertyDeclaration,
    ): String {
        val referencedType = type.arguments
            .singleOrNull()
            ?.type
            ?.resolve()
            ?: error(
                "Unable to resolve referenced type of " +
                        property.simpleName.asString(),
            )

        val referencedClass =
            referencedType.declaration as? KSClassDeclaration
                ?: error(
                    "Referenced type of ${property.simpleName.asString()} " +
                            "is not a class",
                )

        return "${referencedClass.simpleName.asString()}Table"
    }

    private fun KSType.isEncrypted(): Boolean =
        declaration.qualifiedName?.asString() == ENCRYPTED_TYPE

    private fun KSType.isHashed(): Boolean =
        declaration.qualifiedName?.asString() == HASHED_TYPE

    private fun KSType.isId(): Boolean =
        declaration.qualifiedName?.asString() == ID_TYPE

    private fun KSPropertyDeclaration.isInfrastructureProperty(): Boolean {
        val qualifiedName =
            type.resolve().declaration.qualifiedName?.asString()

        return simpleName.asString() == "id" ||
                qualifiedName == EVENTS_TYPE
    }

    private fun KSPropertyDeclaration.hasAnnotation(
        annotationName: String,
    ): Boolean =
        annotations.any {
            it.annotationType.resolve()
                .declaration.qualifiedName?.asString() ==
                    annotationName
        }

    private fun KSPropertyDeclaration.isConstructorProperty(): Boolean {
        val parent =
            parentDeclaration as? KSClassDeclaration
                ?: return false

        val constructor =
            parent.primaryConstructor
                ?: return false

        return constructor.parameters.any { parameter ->
            parameter.name?.asString() ==
                    simpleName.asString()
        }
    }

    private fun String.toSnakeCase(): String =
        replace(
            Regex("([a-z0-9])([A-Z])"),
            "$1_$2",
        ).lowercase()
}
