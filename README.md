# The IELTS Spells Backend

Spring Boot Modular Monolith for CMS, admissions, academic management, learning
activities, progress tracking, attendance, tests, Writing AI, notifications and reporting.

## Requirements

- Java 21
- Maven 3.9+
- Docker Desktop for local PostgreSQL

## Run locally

1. Copy `.env.example` values into your IDE environment.
2. Start PostgreSQL: `docker compose up -d postgres`.
3. Start the app: `mvn spring-boot:run`.
4. Swagger UI: `http://localhost:8080/swagger-ui`.
5. Health: `http://localhost:8080/actuator/health`.

Alternatively, build the complete backend with `docker build -t the-ielts-spells-backend .`.

## Module rules

- Modules are direct subpackages of `com.theieltsspells`.
- Cross-module access goes through public application APIs or events.
- Repositories and JPA entities remain internal to their owner module.
- `ModularityTests` verifies boundaries in CI.

## Database

`V001__bootstrap_schema.sql` is the initial bootstrap migration generated from the approved schema.
Subsequent changes must use new Flyway migrations; never edit a migration already applied remotely.
