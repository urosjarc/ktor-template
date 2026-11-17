# ktor-template

This project uses [Gradle](https://gradle.org/).
To build and run the application, use the *Gradle* tool window by clicking the Gradle icon in the right-hand toolbar,
or run it directly from the terminal:

* Run `./gradlew run` to build and run the application.
* Run `./gradlew build` to only build the application.
* Run `./gradlew check` to run all checks, including tests.
* Run `./gradlew clean` to clean all build outputs.

Note the usage of the Gradle Wrapper (`./gradlew`).
This is the suggested way to use Gradle in production projects.

[Learn more about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

[Learn more about Gradle tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks).

This project follows the suggested multi-module setup and consists of the `core`, `ksp`, `app`, and `api` subprojects.
The shared build logic was extracted to a convention plugin located in `buildSrc`.

This project uses a version catalog (see `gradle/libs.versions.toml`) to declare and version dependencies
and both a build cache and a configuration cache (see `gradle.properties`).

# Project Features & Skills Showcase

This repository serves as an enterprise-grade backend template and showcase of modern, production-ready Kotlin / Ktor architecture. Below is a comprehensive breakdown of the core features, architectural patterns, and engineering skills implemented throughout the project:

### 1. Clean Multi-Module Architecture & Build Engineering
- **Modular Hexagonal Design**: Strict separation of concerns divided into decoupled submodules:
  - `:core` – Pure domain models, value types, use-case contracts, and cryptographic abstractions.
  - `:ksp` – Custom Kotlin Symbol Processing (KSP) annotation processors and code generators.
  - `:app` – Application infrastructure, Exposed R2DBC database services, idempotency engine, pagination compiler, and business logic execution.
  - `:api` – Ktor server setup, HTTP routes, custom security and idempotency plugins, Swagger UI, and observability endpoints.
  - `buildSrc` – Centralized Gradle convention plugins written in Kotlin DSL with type-safe accessors.
- **Modern Build Tooling**: Gradle Version Catalog (`gradle/libs.versions.toml`), incremental compilation, build cache, and configuration cache optimization.

---

### 2. Metaprogramming & KSP Code Generation (`:ksp`)
- **Custom KSP Processors**:
  - `@Persistence`: Scans core domain entities and automatically code-generates Exposed SQL tables, column mappings, and repository layers at compile time.
  - `@Post` / `@Put`: Automatically generates validated DTO models (`DomainPost`, `DomainPut`) from domain classes, eliminating boilerplate while guaranteeing compile-time type safety.

---

### 3. Enterprise Security & Cryptography
- **Field-Level Deterministic Encryption**: Integrates **Google Tink** (Deterministic AEAD) with `@Encrypted` value class and Base64 URL-safe encoding for sensitive data storage.
- **State-of-the-Art Hashing**: Implements **Argon2id** password hashing (`@Hashed` value class) with constant-time verification.
- **Cryptographic JWT Management**: Token generation, signing, and audience verification powered by Google Tink keysets (`TinkeyJwtService`).
- **Custom RBAC Security Plugin (`SecurityPlugin`)**:
  - Declarative route security DSL (`Route.security { role(...); pathValues { ... } }`).
  - Ahead-of-time policy compilation with glob/regex matching and finite path parameter expansion.
  - Default-deny runtime authorization and startup-time verification that detects uncovered routes and generates an ASCII permissions table report.

---

### 4. Distributed Hierarchical Idempotency Engine
- **Tree-Based Idempotency**:
  - Guarantees exactly-once execution semantics across nested, interdependent Use Cases (`UseCase.Executor`).
  - Context-propagated execution hierarchy (`IdempotencyContextElement`) tracking parent-child execution graphs.
  - SHA-256 payload fingerprinting to detect re-execution parameter mutation conflicts.
  - Profiling & ASCII tree reporting with granular serialization and execution timing metrics.

---

### 5. Reactive Database & Dynamic Keyset Pagination
- **Non-blocking Persistence**: JetBrains Exposed built over **R2DBC** connection pooling for high-throughput asynchronous SQL execution.
- **PostgreSQL Database-Level Auditing**:
  - Transaction-scoped context propagation (`set_config('app.user_id')`, `set_config('app.request_id')`).
  - Automated PL/pgSQL triggers (`audit_row_change()`) capturing JSONB diffs of OLD vs NEW rows into `system.logs_audit`.
  - Event triggers (`audit_new_table()`) automatically instrumenting newly created tables.
  - Automated timestamp management (`createdAt`, `insertedAt`, `updatedAt`) via database triggers.
- **Advanced Keyset/Cursor Pagination Compiler (`PaginationCompiler`)**:
  - Compiles multi-column sorted queries with compound boolean expressions for Exposed R2DBC.
  - Tamper-proof, Base64-encoded cursor serialization with checksum validation.
  - Supports dynamic AST-based `WHERE` and `HAVING` filters.

---

### 6. Observability, Logging & Infrastructure
- **Asynchronous Multi-Sink Logging (`Log4j2`)**:
  - Async console, rolling time-based compressed file appenders (`trace`, `info`, `warn`, `error`).
  - Async JDBC logging directly into PostgreSQL (`system.logs_application`) enriched with MDC request ID and user ID.
- **Full Micrometer & Prometheus Metrics**: Exposes JVM memory, GC, threads, processor metrics, and HTTP request latency distribution histograms via `/api/system/metrics`.
- **OpenAPI & Swagger Documentation**: Integrated Swagger UI available at `/api/docs`.
- **Containerized Infrastructure**: Docker Compose configuration for PostgreSQL 18, Redis 8 (with ACLs), and Flyway automated schema migrations and seed scripts.

---

### 7. Modern Kotlin & Idiomatic Design
- **UUIDv7 Primary Keys**: Utilizing time-sortable UUIDv7 (`Id<T>` with Kotlin 2.0+ `Uuid.generateV7()`).
- **Zero-Allocation Domain Primitives**: Extensive use of `@JvmInline value class` for IDs, hashed passwords, and encrypted strings.
- **Custom Kotlinx Serialization**: Polymorphic and custom serializers for binary payloads, time zones, encrypted, and hashed types.
- **Coroutines & Asynchronous Flows**: Non-blocking concurrency management across all layers.

# Tips and trics...

For seeing the docker volumes without changing permissions...

```aiignore
USER=urosjarc
sudo setfacl -R -m u:$USER:rx ./infra/postgres/volume
sudo setfacl -R -m u:$USER:rx ./infra/redis/volume
```
