package com.urosjarc.ktor_template.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance

private const val PUT_ANNOTATION =
    "com.urosjarc.ktor_template.core.annotations.Put"

public class PutProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(
        resolver: Resolver,
    ): List<KSAnnotated> {

        resolver
            .getSymbolsWithAnnotation(PUT_ANNOTATION)
            .filterIsInstance<KSPropertyDeclaration>()
            .groupBy { it.parentDeclaration }
            .forEach { (parent, properties) ->

                val domain = parent as? KSClassDeclaration
                    ?: return@forEach

                generate(domain, properties)
            }

        return emptyList()
    }

    private fun generate(
        domain: KSClassDeclaration,
        properties: List<KSPropertyDeclaration>,
    ) {
        val packageName = "${domain.packageName.asString()}.generated"
        val domainName = domain.simpleName.asString()
        val generatedName = "${domainName}Put"

        val types = properties
            .map { it.type.resolve() }

        val imports = ImportResolver()

        val renderedProperties = properties.mapIndexed { index, property ->
            val type = types[index]

            val name = property.simpleName.asString()
            val renderedType = renderType(type, imports)

            val comma =
                if (index < properties.lastIndex) "," else ""

            "    public val $name: $renderedType$comma"
        }

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = false,
                domain.containingFile!!,
            ),
            packageName = packageName,
            fileName = generatedName,
        )

        file.bufferedWriter().use { out ->

            out.appendLine("package $packageName")
            out.appendLine()

            out.appendLine("import kotlinx.serialization.Serializable")

            imports.imports
                .filterNot { it == "kotlinx.serialization.Serializable" }
                .sorted()
                .forEach { import ->
                    out.appendLine("import $import")
                }

            out.appendLine()

            out.appendLine("@Serializable")
            out.appendLine("public data class $generatedName(")

            renderedProperties.forEach(out::appendLine)

            out.appendLine(")")
        }
    }

    private fun renderType(
        type: KSType,
        imports: ImportResolver,
    ): String {

        val declaration = type.declaration

        val qualifiedName =
            declaration.qualifiedName?.asString()
                ?: return type.toString()

        val typeName =
            if (qualifiedName.startsWith("kotlin.")) {
                qualifiedName.removePrefix("kotlin.")
            } else {
                imports.reference(qualifiedName)
            }

        val arguments = type.arguments

        val rendered =
            if (arguments.isEmpty()) {
                typeName
            } else {
                buildString {
                    append(typeName)
                    append("<")

                    arguments.forEachIndexed { index, argument ->
                        if (index > 0) {
                            append(", ")
                        }

                        append(renderArgument(argument, imports))
                    }

                    append(">")
                }
            }

        return if (type.nullability.name == "NULLABLE") {
            "$rendered?"
        } else {
            rendered
        }
    }

    private fun renderArgument(
        argument: KSTypeArgument,
        imports: ImportResolver,
    ): String {

        if (argument.variance == Variance.STAR) {
            return "*"
        }

        val type = argument.type?.resolve()
            ?: return "*"

        val rendered = renderType(type, imports)

        return when (argument.variance) {
            Variance.COVARIANT -> "out $rendered"
            Variance.CONTRAVARIANT -> "in $rendered"
            Variance.INVARIANT -> rendered
            Variance.STAR -> "*"
        }
    }

    private class ImportResolver {

        private val references = mutableMapOf<String, String>()

        val imports: Set<String>
            get() = references.values.toSet()

        fun reference(
            qualifiedName: String,
        ): String {

            if (qualifiedName.startsWith("kotlin.")) {
                return qualifiedName.removePrefix("kotlin.")
            }

            val simpleName =
                qualifiedName.substringAfterLast(".")

            val existing = references[simpleName]

            if (existing == null) {
                references[simpleName] = qualifiedName
                return simpleName
            }

            if (existing == qualifiedName) {
                return simpleName
            }

            // Name collision: use the fully qualified name.
            return qualifiedName
        }
    }
}
