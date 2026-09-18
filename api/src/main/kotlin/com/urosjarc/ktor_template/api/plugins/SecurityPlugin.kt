package com.urosjarc.ktor_template.api.plugins

import com.urosjarc.ktor_template.api.extend.ext_clientForbiddenAccess
import com.urosjarc.ktor_template.api.extend.ext_clientUnauthorized
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.util.*

// =============================================================================
// Public security DSL
// =============================================================================

fun Route.security(
    block: SecurityConfig.() -> Unit,
): Security {
    val plugin = install(SecurityPlugin) {
        root = this@security
        block()
    }

    /*
     * Route registration happens after plugin installation.
     * AuthorizationCompiler runs on ApplicationStarted, after the complete
     * protected subtree has been registered.
    */
    plugin.buildRoutes(this)

    return plugin.security
}

class SecurityConfig internal constructor() {

    internal lateinit var root: Route

    private val policyBuilder = AuthorizationPolicy.Builder()

    private var jwtConfig: JwtConfig? = null

    private var pathValuesConfig = AuthorizationPathValues.EMPTY

    private val routeBuilders = mutableListOf<Route.() -> Unit>()

    /**
     * Protect against accidental Cartesian-product explosions during startup.
     */
    var maxFiniteCombinations: Int = 100_000

    /**
     * When true, startup fails when a protected route has no matching rule.
     *
     * Runtime is always default-deny, regardless of this setting.
     */
    var failOnUncoveredRoute: Boolean = true

    fun jwt(
        block: JwtConfig.() -> Unit,
    ) {
        check(jwtConfig == null) {
            "JWT configuration has already been defined."
        }

        jwtConfig = JwtConfig().apply(block)
    }

    fun role(
        name: String,
        vararg permissions: String,
    ) {
        policyBuilder.role(
            name = name,
            permissions = permissions.asList(),
        )
    }

    fun pathValues(
        block: AuthorizationPathValues.Builder.() -> Unit,
    ) {
        pathValuesConfig = authorizationPathValues(block)
    }

    /**
     * Register the endpoint tree protected by this Security scope.
     *
     * A path parameter only participates in finite authorization expansion
     * when a value pool is explicitly configured with pathValues { ... }.
     *
     * Query parameters are intentionally ignored.
     */
    fun routes(
        block: Route.() -> Unit,
    ) {
        routeBuilders += block
    }

    internal fun policy(): AuthorizationPolicy = policyBuilder.build()

    internal fun jwt(): JwtConfig = jwtConfig ?: error(
        "JWT configuration is missing. " + "Configure jwt { ... } inside security { ... }.",
    )

    internal fun pathValues(): AuthorizationPathValues = pathValuesConfig

    internal fun routeBuilders(): List<Route.() -> Unit> = routeBuilders.toList()
}

// =============================================================================
// JWT authentication
// =============================================================================

class JwtConfig {

    /**
     * Enables JWT extraction and validation.
     *
     * Disable this for tests when you want to exercise route authorization
     * without creating/signing real JWTs.
     */
    var enabled: Boolean = true

    /**
     * Used only when [enabled] is false.
     *
     * The role is still passed through the normal authorization compiler.
     */
    var testRole: String? = null

    /**
     * Required JWT audience.
     */
    var audience: String = ""

    private var authenticator: (suspend JwtConfig.(
        call: ApplicationCall,
        token: String,
    ) -> SecurityIdentity?)? = null

    /**
     * The block is responsible for decoding and validating the JWT.
     *
     * [audience] is available directly inside the block.
     *
     * Return null when the token is invalid.
     */
    fun authenticate(
        block: suspend JwtConfig.(
            call: ApplicationCall,
            token: String,
        ) -> SecurityIdentity?,
    ) {
        check(authenticator == null) {
            "JWT authentication has already been configured."
        }

        authenticator = block
    }

    internal suspend fun authenticate(
        call: ApplicationCall,
        token: String,
    ): SecurityIdentity? {
        if (!enabled) {
            val role = testRole ?: error(
                "JWT checking is disabled, but JwtConfig.testRole is not configured.",
            )

            return SecurityIdentity(role = role)
        }

        require(audience.isNotBlank()) {
            "JWT audience must not be blank."
        }

        return (authenticator ?: error(
            "JWT authenticator is missing. " + "Configure authenticate { ... } inside jwt { ... }.",
        )).invoke(
            this,
            call,
            token,
        )
    }
}

data class SecurityIdentity(
    val role: String,
)

private val SecurityIdentityAttribute = AttributeKey<SecurityIdentity>("SecurityIdentity")

fun ApplicationCall.securityIdentity(): SecurityIdentity = attributes[SecurityIdentityAttribute]

// =============================================================================
// Authorization rules
// =============================================================================

data class AuthorizationRequest(
    val method: HttpMethod,
    val path: String,
) {
    val value: String
        get() = "${method.value}:$path"
}

/**
 * An authorization rule is parsed once and matched during policy compilation.
 */
class AuthorizationRule private constructor(
    val expression: String,
    val allowed: Boolean,
    private val pattern: AuthorizationPattern,
) {

    fun matches(
        request: AuthorizationRequest,
    ): Boolean = pattern.matches(request)

    override fun toString(): String = "${if (allowed) "ALLOW" else "DENY"} $expression"

    companion object {

        fun parse(
            value: String,
        ): AuthorizationRule {
            val input = value.trim()

            require(input.isNotEmpty()) {
                "Authorization rule must not be empty."
            }

            val allowed = !input.startsWith("!")

            val expression = input.removePrefix("!").trim()

            require(expression.isNotEmpty()) {
                "Authorization rule '$value' must contain an expression."
            }

            return AuthorizationRule(
                expression = expression,
                allowed = allowed,
                pattern = AuthorizationPattern.parse(
                    expression,
                ),
            )
        }
    }
}

private sealed interface AuthorizationPattern {

    fun matches(
        request: AuthorizationRequest,
    ): Boolean

    data class Glob(
        val method: HttpMethod?,
        val path: Regex,
    ) : AuthorizationPattern {

        override fun matches(
            request: AuthorizationRequest,
        ): Boolean = (method == null || method == request.method) && path.matches(request.path)
    }

    data class RegexPattern(
        val regex: Regex,
    ) : AuthorizationPattern {

        override fun matches(
            request: AuthorizationRequest,
        ): Boolean = regex.matches(request.value)
    }

    companion object {

        fun parse(
            expression: String,
        ): AuthorizationPattern {
            if (expression.startsWith("regex(") && expression.endsWith(")")) {
                val body = expression.substring(
                    startIndex = "regex(".length,
                    endIndex = expression.lastIndex,
                ).trim().removeSurrounding("\"").removeSurrounding("'")

                require(body.isNotEmpty()) {
                    "Authorization regex must not be empty in '$expression'."
                }

                return RegexPattern(
                    regex = Regex(body),
                )
            }

            val separator = expression.indexOf(':')

            require(separator > 0) {
                "Invalid authorization rule '$expression'. " + "Expected METHOD:PATH or regex(...)."
            }

            val methodExpression = expression.substring(0, separator).trim()

            val pathExpression = expression.substring(separator + 1).trim()

            require(methodExpression.isNotEmpty()) {
                "Authorization method must not be blank in '$expression'."
            }

            require(pathExpression.isNotEmpty()) {
                "Authorization path must not be blank in '$expression'."
            }

            val method = if (methodExpression == "*") {
                null
            } else {/*
                     * Ktor's parser accepts standard and custom HTTP methods.
                    */
                HttpMethod.parse(methodExpression)
            }

            return Glob(
                method = method,
                path = compilePathGlob(pathExpression),
            )
        }

        private fun compilePathGlob(
            expression: String,
        ): Regex {
            if (expression == "*") {
                return Regex("^/.*$")
            }

            val path = if (expression.startsWith("/")) {
                expression
            } else {
                "/$expression"
            }

            if (path == "/") {
                return Regex("^/$")
            }

            val segments = path.trim('/').split('/').filter(String::isNotEmpty)

            val result = StringBuilder("^")

            segments.forEachIndexed { index, segment ->
                when {
                    segment == "**" && index == segments.lastIndex -> {/*
                         * /users/{tail} matches both /users and descendants.
                        */
                        result.append("(?:/.*)?")
                    }

                    segment == "**" -> {
                        result.append("(?:/[^/]+)*")
                    }

                    segment == "*" -> {
                        result.append("/[^/]+")
                    }

                    else -> {
                        require(!segment.contains('*')) {
                            "Partial path wildcards are not supported in " + "'$expression'. Use '*' for one segment, " + "'**' for recursive matching, or regex(...)."
                        }

                        result.append("/")
                        result.append(Regex.escape(segment))
                    }
                }
            }

            result.append('$')

            return Regex(result.toString())
        }
    }
}

// =============================================================================
// Authorization policy
// =============================================================================

class AuthorizationPolicy private constructor(
    private val rulesByRole: Map<String, List<AuthorizationRule>>,
) {

    val roles: Set<String>
        get() = rulesByRole.keys

    fun rules(
        role: String,
    ): List<AuthorizationRule> = rulesByRole[role].orEmpty()

    /**
     * Reference evaluator.
     *
     * A JWT contains exactly one role.
     * Therefore the last matching rule for that role wins.
     */
    fun allows(
        role: String,
        request: AuthorizationRequest,
    ): Boolean = rules(role).lastOrNull {
        it.matches(request)
    }?.allowed == true

    class Builder {

        private val rules = linkedMapOf<String, MutableList<AuthorizationRule>>()

        fun role(
            name: String,
            vararg permissions: String,
        ) {
            role(
                name = name,
                permissions = permissions.asList(),
            )
        }

        fun role(
            name: String,
            permissions: Iterable<String>,
        ) {
            require(name.isNotBlank()) {
                "Authorization role name must not be blank."
            }

            rules.getOrPut(name) {
                mutableListOf()
            }.addAll(
                permissions.map(
                    AuthorizationRule::parse,
                ),
            )
        }

        internal fun build(): AuthorizationPolicy = AuthorizationPolicy(
            rulesByRole = rules.mapValues { (_, value) ->
                value.toList()
            },
        )
    }
}

class RoleRegistry(
    roles: Set<String>,
) {

    init {
        require(
            roles.size <= Long.SIZE_BITS,
        ) {
            "Security supports at most ${Long.SIZE_BITS} roles."
        }
    }

    private val masks = roles.sorted().mapIndexed { index, role ->
        role to (1L shl index)
    }.toMap()

    fun bit(
        role: String,
    ): Long = masks[role] ?: 0L

    fun all(): Map<String, Long> = masks
}

data class CompiledAuthorization(
    val allowedRoles: Long,
) {

    fun allows(
        roleBit: Long,
    ): Boolean = allowedRoles and roleBit != 0L
}

private data class DecisionCompilation(
    val authorization: CompiledAuthorization,
    val matchedAnyRule: Boolean,
)

// =============================================================================
// Finite URL path values
// =============================================================================

/**
 * Only explicitly configured URL path parameters are expanded.
 *
 * Example:
 *
 *     pathValues {
 *         values(
 *             "name",
 *             "users",
 *             "tasks",
 *             "orders",
 *         )
 *     }
 *
 * An unconfigured parameter such as {id} is opaque and remains one route.
 * Query parameters are never considered.
 */
class AuthorizationPathValues private constructor(
    internal val values: Map<String, Set<String>>,
) {

    operator fun get(
        parameter: String,
    ): Set<String> = values[parameter].orEmpty()

    companion object {
        val EMPTY = AuthorizationPathValues(emptyMap())
    }

    class Builder {

        private val values = linkedMapOf<String, MutableSet<String>>()

        fun values(
            parameter: String,
            vararg values: String,
        ) {
            values(
                parameter = parameter,
                values = values.asList(),
            )
        }

        fun values(
            parameter: String,
            values: Iterable<String>,
        ) {
            require(parameter.isNotBlank()) {
                "Authorization path parameter name must not be blank."
            }

            val normalized = values.map(String::trim).filter(String::isNotEmpty).toSet()

            require(normalized.isNotEmpty()) {
                "Authorization value pool for '$parameter' must not be empty."
            }

            this.values.getOrPut(parameter) {
                linkedSetOf()
            }.addAll(normalized)
        }

        fun build(): AuthorizationPathValues = AuthorizationPathValues(
            values = values.mapValues { (_, values) ->
                values.toSet()
            },
        )
    }
}

fun authorizationPathValues(
    block: AuthorizationPathValues.Builder.() -> Unit,
): AuthorizationPathValues = AuthorizationPathValues.Builder().apply(block).build()

// =============================================================================
// Route templates
// =============================================================================

private val pathParameterRegex = Regex("""\{([^}/]+)}""")

private fun Route.routingNode(): RoutingNode = this as? RoutingNode ?: error(
    "Security must be installed on a Ktor RoutingNode.",
)

/**
 * A route template as seen from the Security scope.
 *
 * Example:
 *   security mounted at /api/v1
 *   Ktor route /api/v1/entities/{name}
 *   authorization template /entities/{name}
 */
data class RouteTemplate(
    val path: String,
    val parameters: List<String>,
) {

    fun render(
        values: Map<String, String>,
    ): String = pathParameterRegex.replace(path) { match ->
        values[match.groupValues[1]] ?: match.value
    }
}

private fun RoutingNode.template(
    securityRoot: RoutingNode,
): RouteTemplate {
    val fullPath = path.ifEmpty { "/" }

    val basePath = securityRoot.path.ifEmpty { "/" }

    val relativePath = relativePath(
        base = basePath,
        path = fullPath,
    )

    return RouteTemplate(
        path = relativePath,
        parameters = pathParameterRegex.findAll(relativePath).map { it.groupValues[1] }.distinct().toList(),
    )
}

private fun relativePath(
    base: String,
    path: String,
): String {
    val normalizedBase = normalizePath(base)

    val normalizedPath = normalizePath(path)

    if (normalizedBase == "/") {
        return normalizedPath
    }

    require(
        normalizedPath == normalizedBase || normalizedPath.startsWith("$normalizedBase/"),
    ) {
        "Protected route '$normalizedPath' is outside security base '$normalizedBase'."
    }

    val relative = normalizedPath.removePrefix(normalizedBase)

    return relative.ifEmpty { "/" }
}

private fun normalizePath(
    path: String,
): String {
    if (path.isBlank() || path == "/") {
        return "/"
    }

    val normalized = if (path.startsWith("/")) path else "/$path"

    return normalized.trimEnd('/').ifEmpty { "/" }
}

private fun RoutingNode.httpMethod(): HttpMethod? {
    var current: RoutingNode? = this

    while (current != null) {
        val selector = current.selector

        if (selector is HttpMethodRouteSelector) {
            return selector.method
        }

        current = current.parent
    }

    return null
}

// =============================================================================
// Compiled finite lookup
// =============================================================================

class FiniteLookupNode(
    val decision: CompiledAuthorization?,
    val children: Map<String, FiniteLookupNode>,
)

class FiniteLookup(
    private val parameters: List<String>,
    private val root: FiniteLookupNode,
    private val default: CompiledAuthorization,
) {

    /**
     * Request-time complexity is O(k), where k is the fixed number of finite
     * path parameters on this route.
     *
     * It is independent of the number of authorization rules and routes.
     */
    fun resolve(
        pathParameters: Parameters,
    ): CompiledAuthorization {
        var node = root

        for (parameter in parameters) {
            val value = pathParameters[parameter] ?: return default

            node = node.children[value] ?: return default
        }

        return node.decision ?: default
    }

    fun overrideCount(): Int = count(root)

    private fun count(
        node: FiniteLookupNode,
    ): Int = (if (node.decision != null) 1 else 0) + node.children.values.sumOf(::count)
}

// =============================================================================
// Compiled authorization table
// =============================================================================

data class CompiledRoute(
    val method: HttpMethod,
    val template: RouteTemplate,
    val finiteValues: Map<String, Set<String>>,
    val default: CompiledAuthorization,
    val finiteLookup: FiniteLookup?,
)

class CompiledAuthorizationTable(
    private val routes: IdentityHashMap<RoutingNode, CompiledRoute>,
    val roles: RoleRegistry,
) {

    /**
     * Primary request-time lookup:
     *
     *     Ktor selected RoutingNode -> compiled authorization
     */
    operator fun get(
        route: RoutingNode,
    ): CompiledRoute? = routes[route]

    fun entries(): List<Pair<RoutingNode, CompiledRoute>> = routes.entries.map {
        it.key to it.value
    }
}

// =============================================================================
// Compiler
// =============================================================================

private object AuthorizationCompiler {

    fun compile(
        root: RoutingNode,
        policy: AuthorizationPolicy,
        pathValues: AuthorizationPathValues,
        maxFiniteCombinations: Int,
        failOnUncoveredRoute: Boolean,
    ): CompiledAuthorizationTable {
        require(maxFiniteCombinations > 0) {
            "maxFiniteCombinations must be greater than zero."
        }

        val roles = RoleRegistry(policy.roles)

        val routes = IdentityHashMap<RoutingNode, CompiledRoute>()

        root.getAllRoutes().forEach { route ->
            val method = route.httpMethod() ?: return@forEach

            val template = route.template(
                securityRoot = root,
            )

            val finiteParameters = template.parameters.filter {
                pathValues[it].isNotEmpty()
            }

            val defaultResult = compileDecision(
                policy = policy,
                roles = roles,
                request = AuthorizationRequest(
                    method = method,
                    path = template.path,
                ),
            )

            val finiteResult = if (finiteParameters.isEmpty()) {
                null
            } else {
                compileFiniteLookup(
                    template = template,
                    method = method,
                    finiteParameters = finiteParameters,
                    pathValues = pathValues,
                    policy = policy,
                    roles = roles,
                    default = defaultResult.authorization,
                    maxFiniteCombinations = maxFiniteCombinations,
                )
            }

            val covered = defaultResult.matchedAnyRule || (finiteResult?.matchedAnyRule == true)

            if (failOnUncoveredRoute && !covered) {
                throw IllegalStateException(
                    "No authorization rule matches protected route " + "${method.value} ${template.path}.",
                )
            }

            routes[route] = CompiledRoute(
                method = method,
                template = template,
                finiteValues = finiteParameters.associateWith {
                    pathValues[it]
                },
                default = defaultResult.authorization,
                finiteLookup = finiteResult?.lookup,
            )
        }

        return CompiledAuthorizationTable(
            routes = routes,
            roles = roles,
        )
    }

    private data class FiniteCompilation(
        val lookup: FiniteLookup,
        val matchedAnyRule: Boolean,
    )

    private fun compileFiniteLookup(
        template: RouteTemplate,
        method: HttpMethod,
        finiteParameters: List<String>,
        pathValues: AuthorizationPathValues,
        policy: AuthorizationPolicy,
        roles: RoleRegistry,
        default: CompiledAuthorization,
        maxFiniteCombinations: Int,
    ): FiniteCompilation? {
        var combinations = 0

        var matchedAnyRule = false

        fun build(
            index: Int,
            values: MutableMap<String, String>,
        ): FiniteLookupNode? {
            if (index == finiteParameters.size) {
                combinations++

                require(
                    combinations <= maxFiniteCombinations,
                ) {
                    "Route '${template.path}' exceeds the maximum of " + "$maxFiniteCombinations finite authorization combinations."
                }

                val result = compileDecision(
                    policy = policy,
                    roles = roles,
                    request = AuthorizationRequest(
                        method = method,
                        path = template.render(values),
                    ),
                )

                matchedAnyRule = matchedAnyRule || result.matchedAnyRule

                return result.authorization.takeIf { it != default }?.let {
                    FiniteLookupNode(
                        decision = it,
                        children = emptyMap(),
                    )
                }
            }

            val parameter = finiteParameters[index]

            val children = linkedMapOf<String, FiniteLookupNode>()

            pathValues[parameter].forEach { value ->
                values[parameter] = value

                val child = build(
                    index = index + 1,
                    values = values,
                )

                if (child != null) {
                    children[value] = child
                }
            }

            values.remove(parameter)

            return if (children.isEmpty()) {
                null
            } else {
                FiniteLookupNode(
                    decision = null,
                    children = children,
                )
            }
        }

        val root = build(
            index = 0,
            values = linkedMapOf(),
        ) ?: return null

        return FiniteCompilation(
            lookup = FiniteLookup(
                parameters = finiteParameters,
                root = root,
                default = default,
            ),
            matchedAnyRule = matchedAnyRule,
        )
    }

    private fun compileDecision(
        policy: AuthorizationPolicy,
        roles: RoleRegistry,
        request: AuthorizationRequest,
    ): DecisionCompilation {
        var allowedRoles = 0L

        var matchedAnyRule = false

        policy.roles.forEach { role ->
            val rule = policy.rules(role).lastOrNull {
                it.matches(request)
            }

            if (rule != null) {
                matchedAnyRule = true
            }

            if (rule?.allowed == true) {
                allowedRoles = allowedRoles or roles.bit(role)
            }
        }

        return DecisionCompilation(
            authorization = CompiledAuthorization(
                allowedRoles = allowedRoles,
            ),
            matchedAnyRule = matchedAnyRule,
        )
    }
}

// =============================================================================
// Runtime
// =============================================================================

private class SecurityRuntime(
    private val table: CompiledAuthorizationTable,
) {

    fun allowed(
        route: RoutingNode,
        pathParameters: Parameters,
        roleBit: Long,
    ): Boolean {/*
         * Protected route not compiled -> deny.
        */
        val compiledRoute = table[route] ?: return false

        val authorization = compiledRoute.finiteLookup?.resolve(pathParameters) ?: compiledRoute.default

        return authorization.allows(roleBit)
    }

    fun roleBit(role: String): Long = table.roles.bit(role)

    fun report(): SecurityReport = SecurityReport.from(table)
}

// =============================================================================
// Ktor integration
// =============================================================================

/**
 * Handle for an installed security scope.
 *
 * The report is available after application startup, when the route tree has
 * been compiled.
 */
class Security internal constructor(
    private val reportProvider: () -> SecurityReport,
) {
    fun report(): SecurityReport = reportProvider()
}

private class SecurityPluginInstance(
    private val config: SecurityConfig,
) {

    private var runtime: SecurityRuntime? = null

    val security = Security {
        runtime?.report() ?: error(
            "Security has not been compiled yet.",
        )
    }

    fun buildRoutes(
        route: Route,
    ) {
        config.routeBuilders().forEach { builder ->
            builder(route)
        }
    }

    fun compile() {
        check(runtime == null) {
            "Security has already been compiled."
        }

        runtime = SecurityRuntime(
            AuthorizationCompiler.compile(
                root = config.root.routingNode(),
                policy = config.policy(),
                pathValues = config.pathValues(),
                maxFiniteCombinations = config.maxFiniteCombinations,
                failOnUncoveredRoute = config.failOnUncoveredRoute,
            ),
        )
    }

    suspend fun authenticate(
        call: ApplicationCall,
    ): SecurityIdentity? {
        val jwt = config.jwt()

        if (!jwt.enabled) {
            return jwt.authenticate(
                call = call,
                token = "",
            )
        }

        val header = call.request.headers[HttpHeaders.Authorization] ?: return null

        val token = header.takeIf {
            it.startsWith(
                prefix = "Bearer ",
                ignoreCase = true,
            )
        }?.substring(7)?.trim()?.takeIf(String::isNotEmpty) ?: return null

        return jwt.authenticate(
            call = call,
            token = token,
        )
    }

    fun runtime(): SecurityRuntime = runtime ?: error(
        "Security has not been compiled yet.",
    )
}

private val SecurityPlugin = object : BaseRouteScopedPlugin<
        SecurityConfig,
        SecurityPluginInstance,
        > {

    override val key = AttributeKey<SecurityPluginInstance>(
        "SecurityPlugin",
    )

    override fun install(
        pipeline: ApplicationCallPipeline,
        configure: SecurityConfig.() -> Unit,
    ): SecurityPluginInstance {
        val config = SecurityConfig().apply(configure)

        val instance = SecurityPluginInstance(config)

        config.root.routingNode().environment.monitor.subscribe(ApplicationStarted) {
/*
 * The protected route tree is complete here.
*/
            instance.compile()
        }

        pipeline.intercept(
            ApplicationCallPipeline.Plugins,
        ) {
            val routingCall = call as? RoutingPipelineCall ?: error(
                "Security requires a Ktor routing call.",
            )

            val identity = instance.authenticate(call)

            if (identity == null) {
                call.ext_clientUnauthorized()
                finish()
                return@intercept
            }

            call.attributes.put(
                SecurityIdentityAttribute,
                identity,
            )

            val runtime = instance.runtime()

            val roleBit = runtime.roleBit(
                identity.role,
            )

            if (!runtime.allowed(
                    route = routingCall.route,
                    pathParameters = routingCall.pathParameters,
                    roleBit = roleBit,
                )
            ) {
                call.ext_clientForbiddenAccess()
                finish()
            }
        }

        return instance
    }
}

// =============================================================================
// Public report
// =============================================================================

data class SecurityReport(
    val roles: List<Role>,
    val routes: List<Route>,
) {

    data class Role(
        val name: String,
        val bit: Long,
    )

    /**
     * One concrete authorization row.
     *
     * The route is expanded only for path parameters configured through
     * pathValues { ... }. Unconfigured parameters remain in {parameter} form.
     *
     * [expansion] makes the source of a concrete row explicit:
     *
     *     name=users
     *     type=users, version=v2
     */
    data class Route(
        val method: String,
        val path: String,
        val expansion: Map<String, String>,
        val allowedRoles: List<String>,
    )

    /**
     * Flat role x route matrix.
     *
     * Example:
     *
     * ROUTE                           EXPANDED      USER   ADMIN   MANAGER
     * GET /entities/users             name=users    ?      ?       ?
     * GET /entities/tasks             name=tasks    ?      ?       ?
     * GET /entities/orders            name=orders   ?      ?       ?
     * GET /users/{id}                 -             ALLOW  ALLOW   DENY
     */
    fun table(): String = buildString {
        appendLine("SECURITY REPORT")
        appendLine()

        if (routes.isEmpty()) {
            appendLine("(no protected routes)")
            return@buildString
        }

        val routeNames = routes.map {
            "${it.method} ${it.path}"
        }

        val expansions = routes.map {
            it.expansion.entries.joinToString(", ") { (name, value) ->
                "$name=$value"
            }.ifEmpty { "-" }
        }

        val routeWidth = maxOf(
            "ROUTE".length,
            routeNames.maxOf(String::length),
        )

        val expansionWidth = maxOf(
            "EXPANDED".length,
            expansions.maxOf(String::length),
        )

        val roleWidths = roles.associate { role ->
            role.name to maxOf(
                role.name.length,
                5,
            )
        }

        append("ROUTE".padEnd(routeWidth))
        append("  ")
        append("EXPANDED".padEnd(expansionWidth))

        roles.forEach { role ->
            append("  ")
            append(
                role.name.padEnd(
                    roleWidths.getValue(role.name),
                ),
            )
        }

        appendLine()

        append("-".repeat(routeWidth))
        append("  ")
        append("-".repeat(expansionWidth))

        roles.forEach { role ->
            append("  ")
            append(
                "-".repeat(
                    roleWidths.getValue(role.name),
                ),
            )
        }

        appendLine()

        routes.forEachIndexed { index, route ->
            append(
                routeNames[index].padEnd(routeWidth),
            )

            append("  ")
            append(
                expansions[index].padEnd(expansionWidth),
            )

            roles.forEach { role ->
                append("  ")
                append(
                    if (role.name in route.allowedRoles)
                        "X"
                    else {
                        " "
                    }.padEnd(
                        roleWidths.getValue(role.name),
                    ),
                )
            }

            appendLine()
        }
    }

    companion object {

        fun from(
            table: CompiledAuthorizationTable,
        ): SecurityReport {
            val roleMasks = table.roles.all()

            val routes = table.entries().flatMap { (_, compiled) ->
                expandRoutes(
                    compiled = compiled,
                    roleMasks = roleMasks,
                )
            }.sortedWith(
                compareBy<Route> { it.path }.thenBy { it.method }.thenBy {
                    it.expansion.entries.joinToString("\u0000") { entry ->
                        "${entry.key}=${entry.value}"
                    }
                },
            )

            return SecurityReport(
                roles = roleMasks.map { (name, bit) ->
                    Role(
                        name = name,
                        bit = bit,
                    )
                },
                routes = routes,
            )
        }

        private fun expandRoutes(
            compiled: CompiledRoute,
            roleMasks: Map<String, Long>,
        ): List<Route> {
            val finiteValues = compiled.finiteValues

            if (finiteValues.isEmpty()) {
                return listOf(
                    Route(
                        method = compiled.method.value,
                        path = compiled.template.path,
                        expansion = emptyMap(),
                        allowedRoles = roleMasks.filter { (_, bit) ->
                            compiled.default.allows(bit)
                        }.map { it.key },
                    ),
                )
            }

            val parameters = finiteValues.keys.toList()

            val result = mutableListOf<Route>()

            fun expand(
                index: Int,
                values: MutableMap<String, String>,
            ) {
                if (index == parameters.size) {
                    val authorization = compiled.finiteLookup?.resolve(
                        Parameters.build {
                            values.forEach { (name, value) ->
                                append(name, value)
                            }
                        },
                    ) ?: compiled.default

                    result += Route(
                        method = compiled.method.value,
                        path = compiled.template.render(values),
                        expansion = values.toMap(),
                        allowedRoles = roleMasks.filter { (_, bit) ->
                            authorization.allows(bit)
                        }.map { it.key },
                    )

                    return
                }

                val parameter = parameters[index]

                finiteValues[parameter]?.forEach { value ->
                    values[parameter] = value

                    expand(
                        index = index + 1,
                        values = values,
                    )
                }

                values.remove(parameter)
            }

            expand(
                index = 0,
                values = linkedMapOf(),
            )

            return result
        }
    }
}
