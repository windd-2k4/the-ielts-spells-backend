# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21 Spring Boot modular monolith. Production code lives under `src/main/java/com/theieltsspells`; each direct subpackage (for example, `academic`, `cms`, or `progress`) is a business module. Within modules, organize code by `domain`, `application`, `infrastructure`, and `presentation`. Keep JPA entities and repositories internal to their owner; communicate across modules through public application APIs or events. Cross-cutting code belongs in `shared`.

Configuration and Flyway migrations are in `src/main/resources`; add database changes as new files such as `db/migration/V002__add_example.sql`. Never edit a migration already deployed. Tests mirror the main package tree under `src/test/java`. Build output in `target/` is generated and must not be committed.

## Build, Test, and Development Commands

- `docker compose up -d postgres` starts the local PostgreSQL 16 service.
- `mvn spring-boot:run` runs the API at port 8080; Swagger UI is at `/swagger-ui`.
- `mvn test` runs the JUnit test suite.
- `mvn verify` performs the CI-equivalent build and module-boundary verification.
- `mvn clean package` creates the executable artifact under `target/`.
- `docker build -t the-ielts-spells-backend .` builds the application image.

## Coding Style & Naming Conventions

Use four-space indentation and standard Java conventions: PascalCase types, camelCase methods and fields, and lowercase package names. Name Spring components by responsibility (`CourseApplicationService`, `ClassRepository`, `PublicContentController`) and DTOs by intent (`CreateCourseRequest`, `CourseResponse`). Prefer constructor injection and keep transaction boundaries in application services. No formatter or linter is configured, so match nearby code and keep imports and methods readable.

## Testing Guidelines

Tests use JUnit 5, Spring Boot Test, Spring Security Test, Spring Modulith Test, and Testcontainers PostgreSQL. Name test classes `*Tests` and methods after observable behavior, such as `verifiesModuleBoundaries`. Add focused unit tests for business rules and integration tests for persistence, security, or module interactions. Run `mvn verify` before opening a pull request.

## Commit & Pull Request Guidelines

The repository currently has no commit history to establish a convention. Use concise, imperative commit subjects, optionally scoped, such as `academic: validate enrollment status`. Keep commits focused. Pull requests should explain the change and validation performed, link relevant issues, call out migrations or configuration changes, and include API examples when contracts change. Ensure CI passes.

## Security & Configuration

Copy values from `.env.example` into your local environment. Never commit `.env`, credentials, JWTs, or production connection strings. Document any newly required variable in `.env.example` with a safe placeholder.
