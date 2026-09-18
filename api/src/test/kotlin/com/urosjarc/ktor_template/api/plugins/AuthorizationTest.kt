package com.urosjarc.ktor_template.api.plugins

import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecurityPluginTest {

    // =========================================================================
    // AuthorizationRequest
    // =========================================================================

    @Test
    fun `authorization request builds canonical value`() {
        val request = AuthorizationRequest(
            method = HttpMethod.Get,
            path = "/users/123",
        )

        assertEquals(
            "GET:/users/123",
            request.value,
        )
    }

    // =========================================================================
    // AuthorizationRule parsing and matching
    // =========================================================================

    @Test
    fun `exact rule matches exact method and path`() {
        val rule = AuthorizationRule.parse("GET:/users")

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Post, "/users"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users/123"),
            ),
        )
    }

    @Test
    fun `wildcard method matches every method`() {
        val rule = AuthorizationRule.parse("*:/users")

        assertTrue(rule.matches(AuthorizationRequest(HttpMethod.Get, "/users")))
        assertTrue(rule.matches(AuthorizationRequest(HttpMethod.Post, "/users")))
        assertTrue(rule.matches(AuthorizationRequest(HttpMethod.Put, "/users")))
        assertTrue(rule.matches(AuthorizationRequest(HttpMethod.Delete, "/users")))
    }

    @Test
    fun `global wildcard matches every method and path`() {
        val rule = AuthorizationRule.parse("*:*")

        assertTrue(rule.matches(AuthorizationRequest(HttpMethod.Get, "/")))
        assertTrue(rule.matches(AuthorizationRequest(HttpMethod.Post, "/users")))
        assertTrue(
            rule.matches(
                AuthorizationRequest(
                    HttpMethod.Delete,
                    "/users/123/profile",
                ),
            ),
        )
    }

    @Test
    fun `single star matches exactly one path segment`() {
        val rule = AuthorizationRule.parse("GET:/users/*")

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users/123"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users/123/profile"),
            ),
        )
    }

    @Test
    fun `double star at end matches zero or more path segments`() {
        val rule = AuthorizationRule.parse("GET:/users/**")

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users"),
            ),
        )

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users/123"),
            ),
        )

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users/123/profile"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Post, "/users/123"),
            ),
        )
    }

    @Test
    fun `double star in middle matches nested paths`() {
        val rule = AuthorizationRule.parse("GET:/api/**/users")

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/api/users"),
            ),
        )

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/api/v1/users"),
            ),
        )

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/api/v1/admin/users"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/api/v1/admin/users/123"),
            ),
        )
    }

    @Test
    fun `deny rule is parsed as denied`() {
        val rule = AuthorizationRule.parse("!DELETE:/users/**")

        assertEquals("DELETE:/users/**", rule.expression)
        assertFalse(rule.allowed)

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Delete, "/users/123"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/users/123"),
            ),
        )
    }

    @Test
    fun `allow rule is parsed as allowed`() {
        val rule = AuthorizationRule.parse("GET:/users/**")

        assertEquals("GET:/users/**", rule.expression)
        assertTrue(rule.allowed)
    }

    @Test
    fun `leading and trailing whitespace is removed`() {
        val rule = AuthorizationRule.parse("  GET:/users/**  ")

        assertEquals(
            "GET:/users/**",
            rule.expression,
        )
    }

    @Test
    fun `regex rule matches complete authorization value`() {
        val rule = AuthorizationRule.parse(
            """regex("^GET:/reports/[0-9]+$")""",
        )

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/reports/123"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/reports/abc"),
            ),
        )

        assertFalse(
            rule.matches(
                AuthorizationRequest(HttpMethod.Post, "/reports/123"),
            ),
        )
    }

    @Test
    fun `regex rule supports single quoted pattern`() {
        val rule = AuthorizationRule.parse(
            """regex('^GET:/reports/[0-9]+$')""",
        )

        assertTrue(
            rule.matches(
                AuthorizationRequest(HttpMethod.Get, "/reports/123"),
            ),
        )
    }

    @Test
    fun `authorization rule toString reports decision`() {
        assertEquals(
            "ALLOW GET:/users/**",
            AuthorizationRule.parse("GET:/users/**").toString(),
        )

        assertEquals(
            "DENY DELETE:/users/**",
            AuthorizationRule.parse("!DELETE:/users/**").toString(),
        )
    }

    // =========================================================================
    // AuthorizationRule validation
    // =========================================================================

    @Test
    fun `empty rule is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationRule.parse("")
        }
    }

    @Test
    fun `whitespace only rule is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationRule.parse("   ")
        }
    }

    @Test
    fun `deny without expression is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationRule.parse("!")
        }
    }

    @Test
    fun `missing method is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationRule.parse(":/users")
        }
    }

    @Test
    fun `missing path is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationRule.parse("GET:")
        }
    }

    @Test
    fun `missing separator is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationRule.parse("/users/**")
        }
    }

    @Test
    fun `partial path wildcard is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationRule.parse("GET:/user*")
        }
    }

    @Test
    fun `empty regex is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizationRule.parse("""regex("")""")
        }
    }

    // =========================================================================
    // SecurityConfig.role -> AuthorizationPolicy
    // =========================================================================

    @Test
    fun `role adds role to policy`() {
        val config = SecurityConfig()

        config.role(
            "USER",
            "GET:/users/**",
        )

        assertEquals(
            setOf("USER"),
            config.policy().roles,
        )
    }

    @Test
    fun `role adds permission to policy`() {
        val config = SecurityConfig()

        config.role(
            "USER",
            "GET:/users/**",
        )

        assertEquals(
            listOf("GET:/users/**"),
            config.policy()
                .rules("USER")
                .map { it.expression },
        )
    }

    @Test
    fun `role adds multiple permissions in declaration order`() {
        val config = SecurityConfig()

        config.role(
            "USER",
            "GET:/users/**",
            "POST:/users",
            "!DELETE:/users/**",
        )

        val rules = config.policy().rules("USER")

        assertEquals(
            listOf(
                "GET:/users/**",
                "POST:/users",
                "DELETE:/users/**",
            ),
            rules.map { it.expression },
        )

        assertTrue(rules[0].allowed)
        assertTrue(rules[1].allowed)
        assertFalse(rules[2].allowed)
    }

    @Test
    fun `repeated role declarations append permissions`() {
        val config = SecurityConfig()

        config.role("USER", "GET:/users/**")
        config.role("USER", "POST:/users")

        assertEquals(
            listOf(
                "GET:/users/**",
                "POST:/users",
            ),
            config.policy()
                .rules("USER")
                .map { it.expression },
        )
    }

    @Test
    fun `multiple roles are stored independently`() {
        val config = SecurityConfig()

        config.role("USER", "GET:/users/**")
        config.role("ADMIN", "*:*")

        assertEquals(
            setOf("USER", "ADMIN"),
            config.policy().roles,
        )

        assertEquals(
            listOf("GET:/users/**"),
            config.policy()
                .rules("USER")
                .map { it.expression },
        )

        assertEquals(
            listOf("*:*"),
            config.policy()
                .rules("ADMIN")
                .map { it.expression },
        )
    }

    @Test
    fun `blank role name is rejected`() {
        val config = SecurityConfig()

        assertFailsWith<IllegalArgumentException> {
            config.role(
                "   ",
                "GET:/users/**",
            )
        }
    }

    // =========================================================================
    // AuthorizationPolicy - one role only
    // =========================================================================

    @Test
    fun `policy allows matching request for role`() {
        val config = SecurityConfig()
        config.role("USER", "GET:/users/**")

        assertTrue(
            config.policy().allows(
                role = "USER",
                request = AuthorizationRequest(
                    method = HttpMethod.Get,
                    path = "/users/123",
                ),
            ),
        )
    }

    @Test
    fun `policy denies wrong method for role`() {
        val config = SecurityConfig()
        config.role("USER", "GET:/users/**")

        assertFalse(
            config.policy().allows(
                role = "USER",
                request = AuthorizationRequest(
                    method = HttpMethod.Post,
                    path = "/users/123",
                ),
            ),
        )
    }

    @Test
    fun `policy denies unmatched path for role`() {
        val config = SecurityConfig()
        config.role("USER", "GET:/users/**")

        assertFalse(
            config.policy().allows(
                role = "USER",
                request = AuthorizationRequest(
                    method = HttpMethod.Get,
                    path = "/orders/123",
                ),
            ),
        )
    }

    @Test
    fun `policy denies unknown role`() {
        val config = SecurityConfig()
        config.role("ADMIN", "*:*")

        assertFalse(
            config.policy().allows(
                role = "USER",
                request = AuthorizationRequest(
                    method = HttpMethod.Get,
                    path = "/anything",
                ),
            ),
        )
    }

    @Test
    fun `policy uses last matching rule for one role`() {
        val config = SecurityConfig()

        config.role(
            "USER",
            "GET:/users/**",
            "!GET:/users/private",
            "GET:/users/private/profile",
        )

        val policy = config.policy()

        assertTrue(
            policy.allows(
                role = "USER",
                request = AuthorizationRequest(
                    HttpMethod.Get,
                    "/users/123",
                ),
            ),
        )

        assertFalse(
            policy.allows(
                role = "USER",
                request = AuthorizationRequest(
                    HttpMethod.Get,
                    "/users/private",
                ),
            ),
        )

        assertTrue(
            policy.allows(
                role = "USER",
                request = AuthorizationRequest(
                    HttpMethod.Get,
                    "/users/private/profile",
                ),
            ),
        )
    }

    @Test
    fun `policy evaluates only the supplied role`() {
        val config = SecurityConfig()

        config.role("USER", "GET:/users/**")
        config.role("ADMIN", "DELETE:/users/**")

        val policy = config.policy()

        assertTrue(
            policy.allows(
                role = "USER",
                request = AuthorizationRequest(
                    HttpMethod.Get,
                    "/users/123",
                ),
            ),
        )

        assertFalse(
            policy.allows(
                role = "USER",
                request = AuthorizationRequest(
                    HttpMethod.Delete,
                    "/users/123",
                ),
            ),
        )

        assertTrue(
            policy.allows(
                role = "ADMIN",
                request = AuthorizationRequest(
                    HttpMethod.Delete,
                    "/users/123",
                ),
            ),
        )
    }

    @Test
    fun `unknown role has no rules`() {
        val config = SecurityConfig()
        config.role("USER", "GET:/users/**")

        assertTrue(
            config.policy()
                .rules("UNKNOWN")
                .isEmpty(),
        )
    }

    // =========================================================================
    // RoleRegistry
    // =========================================================================

    @Test
    fun `role registry assigns deterministic sorted bits`() {
        val registry = RoleRegistry(
            setOf(
                "USER",
                "ADMIN",
                "AUDITOR",
            ),
        )

        assertEquals(1L, registry.bit("ADMIN"))
        assertEquals(2L, registry.bit("AUDITOR"))
        assertEquals(4L, registry.bit("USER"))
    }

    @Test
    fun `role registry returns all role bits`() {
        val registry = RoleRegistry(
            setOf(
                "USER",
                "ADMIN",
            ),
        )

        assertEquals(
            mapOf(
                "ADMIN" to 1L,
                "USER" to 2L,
            ),
            registry.all(),
        )
    }

    @Test
    fun `role registry returns zero for unknown role`() {
        val registry = RoleRegistry(
            setOf("ADMIN"),
        )

        assertEquals(
            0L,
            registry.bit("USER"),
        )
    }

    @Test
    fun `role registry allows exactly 64 roles`() {
        val registry = RoleRegistry(
            (1..64)
                .map { "ROLE_$it" }
                .toSet(),
        )

        assertEquals(
            64,
            registry.all().size,
        )
    }

    @Test
    fun `role registry rejects more than 64 roles`() {
        val roles = (1..65)
            .map { "ROLE_$it" }
            .toSet()

        assertFailsWith<IllegalArgumentException> {
            RoleRegistry(roles)
        }
    }

    // =========================================================================
    // CompiledAuthorization
    // =========================================================================

    @Test
    fun `compiled authorization allows included role bit`() {
        val authorization = CompiledAuthorization(
            allowedRoles = 0b1010,
        )

        assertTrue(authorization.allows(0b0010))
        assertTrue(authorization.allows(0b1000))
    }

    @Test
    fun `compiled authorization denies excluded role bit`() {
        val authorization = CompiledAuthorization(
            allowedRoles = 0b1010,
        )

        assertFalse(authorization.allows(0b0001))
        assertFalse(authorization.allows(0b0100))
        assertFalse(authorization.allows(0))
    }

    // =========================================================================
    // AuthorizationPathValues
    // =========================================================================

    @Test
    fun `path values stores finite values`() {
        val values = authorizationPathValues {
            values(
                "name",
                "users",
                "tasks",
                "orders",
            )
        }

        assertEquals(
            setOf(
                "users",
                "tasks",
                "orders",
            ),
            values["name"],
        )
    }

    @Test
    fun `path values merge repeated declarations`() {
        val values = authorizationPathValues {
            values("name", "users", "tasks")
            values("name", "tasks", "orders")
        }

        assertEquals(
            setOf(
                "users",
                "tasks",
                "orders",
            ),
            values["name"],
        )
    }

    @Test
    fun `path values trim and remove blank values`() {
        val values = authorizationPathValues {
            values(
                "name",
                " users ",
                "tasks",
                " ",
                "",
                "orders",
            )
        }

        assertEquals(
            setOf(
                "users",
                "tasks",
                "orders",
            ),
            values["name"],
        )
    }

    @Test
    fun `unknown path parameter has no finite values`() {
        val values = authorizationPathValues {
            values("name", "users")
        }

        assertTrue(
            values["id"].isEmpty(),
        )
    }

    @Test
    fun `blank path parameter name is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            authorizationPathValues {
                values("", "users")
            }
        }
    }

    @Test
    fun `blank-only finite value pool is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            authorizationPathValues {
                values(
                    "name",
                    "",
                    " ",
                    "   ",
                )
            }
        }
    }

    @Test
    fun `path values are immutable snapshots`() {
        val builder = AuthorizationPathValues.Builder()

        builder.values("name", "users")
        val first = builder.build()

        builder.values("name", "tasks")
        val second = builder.build()

        assertEquals(
            setOf("users"),
            first["name"],
        )

        assertEquals(
            setOf("users", "tasks"),
            second["name"],
        )
    }

    // =========================================================================
    // RouteTemplate
    // =========================================================================

    @Test
    fun `route template renders configured parameters`() {
        val template = RouteTemplate(
            path = "/entities/{name}/{id}",
            parameters = listOf("name", "id"),
        )

        assertEquals(
            "/entities/users/{id}",
            template.render(
                mapOf(
                    "name" to "users",
                ),
            ),
        )

        assertEquals(
            "/entities/users/123",
            template.render(
                mapOf(
                    "name" to "users",
                    "id" to "123",
                ),
            ),
        )
    }

    @Test
    fun `route template leaves missing parameters untouched`() {
        val template = RouteTemplate(
            path = "/users/{id}",
            parameters = listOf("id"),
        )

        assertEquals(
            "/users/{id}",
            template.render(emptyMap()),
        )
    }

    // =========================================================================
    // FiniteLookup
    // =========================================================================

    @Test
    fun `finite lookup resolves configured value`() {
        val default = CompiledAuthorization(
            allowedRoles = 1L,
        )

        val users = CompiledAuthorization(
            allowedRoles = 2L,
        )

        val lookup = FiniteLookup(
            parameters = listOf("name"),
            root = FiniteLookupNode(
                decision = null,
                children = mapOf(
                    "users" to FiniteLookupNode(
                        decision = users,
                        children = emptyMap(),
                    ),
                ),
            ),
            default = default,
        )

        assertEquals(
            users,
            lookup.resolve(
                Parameters.build {
                    append("name", "users")
                },
            ),
        )
    }

    @Test
    fun `finite lookup falls back to default for unknown value`() {
        val default = CompiledAuthorization(
            allowedRoles = 1L,
        )

        val lookup = FiniteLookup(
            parameters = listOf("name"),
            root = FiniteLookupNode(
                decision = null,
                children = mapOf(
                    "users" to FiniteLookupNode(
                        decision = CompiledAuthorization(2L),
                        children = emptyMap(),
                    ),
                ),
            ),
            default = default,
        )

        assertEquals(
            default,
            lookup.resolve(
                Parameters.build {
                    append("name", "orders")
                },
            ),
        )
    }

    @Test
    fun `finite lookup falls back to default when parameter is missing`() {
        val default = CompiledAuthorization(
            allowedRoles = 1L,
        )

        val lookup = FiniteLookup(
            parameters = listOf("name"),
            root = FiniteLookupNode(
                decision = null,
                children = emptyMap(),
            ),
            default = default,
        )

        assertEquals(
            default,
            lookup.resolve(Parameters.Empty),
        )
    }

    @Test
    fun `finite lookup resolves multiple parameters`() {
        val default = CompiledAuthorization(
            allowedRoles = 1L,
        )

        val concrete = CompiledAuthorization(
            allowedRoles = 2L,
        )

        val lookup = FiniteLookup(
            parameters = listOf(
                "type",
                "version",
            ),
            root = FiniteLookupNode(
                decision = null,
                children = mapOf(
                    "orders" to FiniteLookupNode(
                        decision = null,
                        children = mapOf(
                            "v2" to FiniteLookupNode(
                                decision = concrete,
                                children = emptyMap(),
                            ),
                        ),
                    ),
                ),
            ),
            default = default,
        )

        assertEquals(
            concrete,
            lookup.resolve(
                Parameters.build {
                    append("type", "orders")
                    append("version", "v2")
                },
            ),
        )
    }

    @Test
    fun `finite lookup counts concrete overrides`() {
        val lookup = FiniteLookup(
            parameters = listOf("name"),
            root = FiniteLookupNode(
                decision = null,
                children = mapOf(
                    "users" to FiniteLookupNode(
                        decision = CompiledAuthorization(1L),
                        children = emptyMap(),
                    ),
                    "tasks" to FiniteLookupNode(
                        decision = CompiledAuthorization(2L),
                        children = emptyMap(),
                    ),
                ),
            ),
            default = CompiledAuthorization(4L),
        )

        assertEquals(
            2,
            lookup.overrideCount(),
        )
    }

    // =========================================================================
    // JwtConfig
    // =========================================================================

    @Test
    fun `jwt config defaults are correct`() {
        val config = JwtConfig()

        assertTrue(config.enabled)
        assertEquals("", config.audience)
        assertEquals(null, config.testRole)
    }

    @Test
    fun `jwt config accepts disabled test mode`() {
        val config = JwtConfig().apply {
            enabled = false
            testRole = "ADMIN"
        }

        assertFalse(config.enabled)
        assertEquals("ADMIN", config.testRole)
    }

    @Test
    fun `jwt authenticator can be configured`() {
        val config = JwtConfig()

        config.authenticate { _, _ ->
            SecurityIdentity(role = "USER")
        }
    }

    @Test
    fun `jwt authenticator cannot be configured twice`() {
        val config = JwtConfig()

        config.authenticate { _, _ ->
            SecurityIdentity(role = "USER")
        }

        assertFailsWith<IllegalStateException> {
            config.authenticate { _, _ ->
                SecurityIdentity(role = "ADMIN")
            }
        }
    }

    // =========================================================================
    // SecurityIdentity
    // =========================================================================

    @Test
    fun `security identity contains one role`() {
        val identity = SecurityIdentity(
            role = "ADMIN",
        )

        assertEquals(
            "ADMIN",
            identity.role,
        )
    }

    // =========================================================================
    // SecurityConfig defaults
    // =========================================================================

    @Test
    fun `security config defaults are correct`() {
        val config = SecurityConfig()

        assertEquals(
            100_000,
            config.maxFiniteCombinations,
        )

        assertTrue(
            config.failOnUncoveredRoute,
        )

        assertTrue(
            config.pathValues()["id"].isEmpty(),
        )
    }

    @Test
    fun `security config can disable uncovered route failure`() {
        val config = SecurityConfig()

        config.failOnUncoveredRoute = false

        assertFalse(
            config.failOnUncoveredRoute,
        )
    }

    @Test
    fun `security config rejects second jwt configuration`() {
        val config = SecurityConfig()

        config.jwt {
            enabled = false
            testRole = "USER"
        }

        assertFailsWith<IllegalStateException> {
            config.jwt {
                enabled = false
                testRole = "ADMIN"
            }
        }
    }

    // =========================================================================
    // SecurityReport
    // =========================================================================

    @Test
    fun `security report table contains role matrix`() {
        val report = SecurityReport(
            roles = listOf(
                SecurityReport.Role(
                    name = "ADMIN",
                    bit = 1L,
                ),
                SecurityReport.Role(
                    name = "USER",
                    bit = 2L,
                ),
            ),
            routes = listOf(
                SecurityReport.Route(
                    method = "GET",
                    path = "/entities/orders",
                    expansion = mapOf("name" to "orders"),
                    allowedRoles = listOf(
                        "ADMIN",
                        "USER",
                    ),
                ),
                SecurityReport.Route(
                    method = "POST",
                    path = "/entities/orders",
                    expansion = mapOf("name" to "orders"),
                    allowedRoles = listOf("ADMIN"),
                ),
                SecurityReport.Route(
                    method = "GET",
                    path = "/users/{id}",
                    expansion = emptyMap(),
                    allowedRoles = listOf(
                        "ADMIN",
                        "USER",
                    ),
                ),
            ),
        )

        val table = report.table()

        assertTrue(table.contains("SECURITY REPORT"))
        assertTrue(table.contains("ROUTE"))
        assertTrue(table.contains("EXPANDED"))
        assertTrue(table.contains("ADMIN"))
        assertTrue(table.contains("USER"))
        assertTrue(table.contains("GET /entities/orders"))
        assertTrue(table.contains("name=orders"))
        assertTrue(table.contains("GET /users/{id}"))
        assertTrue(table.contains("X"))
    }

    @Test
    fun `empty security report reports no protected routes`() {
        val report = SecurityReport(
            roles = emptyList(),
            routes = emptyList(),
        )

        assertTrue(
            report.table().contains("(no protected routes)"),
        )
    }

    @Test
    fun `security report route retains expansion`() {
        val route = SecurityReport.Route(
            method = "GET",
            path = "/entities/orders",
            expansion = mapOf("name" to "orders"),
            allowedRoles = listOf("ADMIN"),
        )

        assertEquals(
            mapOf("name" to "orders"),
            route.expansion,
        )

        assertEquals(
            listOf("ADMIN"),
            route.allowedRoles,
        )
    }

    // =========================================================================
    // Security handle
    // =========================================================================

    @Test
    fun `security handle delegates report`() {
        val expected = SecurityReport(
            roles = listOf(
                SecurityReport.Role(
                    name = "ADMIN",
                    bit = 1L,
                ),
            ),
            routes = emptyList(),
        )

        val security = Security {
            expected
        }

        assertEquals(
            expected,
            security.report(),
        )
    }
}
