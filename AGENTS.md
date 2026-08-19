# AGENTS.md — The IELTS Spells Backend

This file is the operating contract for AI agents working in this repository. Read it before inspecting or changing code. The sibling frontend repository is normally at `../the-ielts-spells-frontend`; its `AGENTS.md` contains the matching client-side contract.

## 1. Product and architecture facts

- Stack: Java 21, Spring Boot 3.5, Maven, Spring Modulith, Spring Security Resource Server, JPA, Flyway, PostgreSQL 16, Testcontainers.
- Runtime: API on `http://localhost:8080`; base path `/api/v1`; Swagger UI at `/swagger-ui`; health at `/actuator/health`.
- Architecture: modular monolith under `src/main/java/com/theieltsspells`.
- A business module owns its domain model, persistence, use cases, and HTTP endpoints. Other modules call its public application API or react to events; they must not reach into its repositories.
- Standard module layers are `domain`, `application`, `infrastructure`, and `presentation`. Cross-cutting web and technical code belongs in `shared`.
- Current modules include `academic`, `admissions`, `assignment`, `attendance`, `audit`, `cms`, `curriculum`, `identity`, `learninglibrary`, `notification`, `progress`, `reporting`, `testing`, and `writingevaluation`.

### Non-negotiable domain decisions

- `Course` is the teaching cohort/class aggregate. Do not recreate a separate class aggregate, class CRUD, or duplicated class route unless the user explicitly changes this decision and a migration plan is approved.
- One course teaches exactly one skill pair: `LISTENING_READING` or `SPEAKING_WRITING`. Never expose all four skills as if they belong to one course.
- A student may have many enrollments. Enrollment lifecycle, transfer, reservation/deferral, withdrawal, and course capacity belong to the academic domain; do not implement them as ad-hoc student flags.
- Attendance is session based. Every currently enrolled student must be visible for every course session; a student enrolled after the session remains visible with an unmarked/not-applicable explanation rather than silently disappearing.
- Supabase provides authentication and may provide object storage. PostgreSQL remains the source of truth for business data. The frontend must not write business tables directly through Supabase.
- The Reading Test Builder uses `tests.builder_content` as an editable draft document. Published/runnable tests must be validated and materialized into normalized test sections, question groups, questions, options, and answers. Student delivery must never depend on an unvalidated draft JSON document.

## 2. Work safely in the existing repository

Before editing:

1. Read this file, the root `README.md`, relevant module code, and the sibling frontend `AGENTS.md` for cross-repository work.
2. Run `git status --short`. The worktree may contain user changes. Preserve them and never reset, checkout, delete, reformat, or overwrite unrelated files.
3. Search before inventing: use `rg` to find the current entity, DTO, endpoint, migration, enum, error handler, and test convention.
4. For work spanning several files or both repositories, state a short plan and define the contract first.
5. Do not claim a defect is fixed until the relevant verification has actually run.

Never use `git reset --hard`, `git checkout --`, broad recursive deletion, or automatic database reset. Never expose values from `.env`, JWTs, Supabase service-role keys, database passwords, or signed URLs in logs, patches, screenshots, tests, or responses.

## 3. Ownership and dependency rules

- `presentation`: HTTP mapping, authentication context, validation entry point, response status. Controllers remain thin.
- `application`: use cases, orchestration, authorization-sensitive workflow, transactions, DTO mapping.
- `domain`: entities/value objects, lifecycle transitions, invariants, domain errors. Keep framework dependencies minimal.
- `infrastructure`: JPA repositories, external APIs, storage, email, Supabase, Zoom, and other adapters.
- Return request/response DTOs, never JPA entities.
- Keep `@Transactional` at application-service/use-case boundaries. Use `readOnly = true` for queries where appropriate.
- Prefer constructor injection. Avoid static service locators and field injection.
- Cross-module database joins or repository imports require an explicit architecture reason; prefer application APIs, projections owned by a reporting module, or events.
- Keep names intent based: `CreateCourseRequest`, `CourseResponse`, `EnrollmentApplicationService`, `AttendanceAdminController`.

## 4. Contract-first cross-repository workflow

For any feature consumed by a frontend, follow this sequence:

1. **Clarify the use case**: actor, required authority, preconditions, lifecycle transition, failure cases, and empty state.
2. **Inspect the current contract**: existing controller, DTO, enum, `PageResponse`, frontend call site, and UI state. Extend instead of creating a parallel endpoint.
3. **Change backend first when the contract changes**: migration → domain/entity → repository → application service → DTO → controller/security → tests/OpenAPI.
4. **Make the contract explicit**: method, `/api/v1` path, request, response, pagination, enum values, error statuses, and permissions.
5. **Synchronize the sibling frontend in the same task** when requested or when a breaking change would otherwise leave it unusable. Update shared TypeScript contracts/client code before page components.
6. **Verify both sides** and smoke-test the real flow. Do not add fake fallback data to make a broken endpoint appear successful.

If only one repository is in scope, leave a precise handoff containing the required sibling change. Do not silently ship a breaking contract.

### API conventions

- Admin endpoints live under `/api/v1/admin/...`; public/student endpoints must be separate and deliberately authorized.
- Use UUIDs as identifiers and ISO-8601 date/time strings with an explicit timezone policy. Do not introduce locale-formatted dates into API payloads.
- Use the shared `com.theieltsspells.shared.web.PageResponse<T>` for paginated lists. Keep `page`, `size`, and `sort` semantics consistent.
- Validate input with Jakarta Validation and business rules in the application/domain layer. Return meaningful 400, 404, 409, and 422 responses instead of a generic 500.
- Reuse the global error envelope. Never return stack traces or provider secrets.
- Enum wire values are a contract. Changing one requires a migration/backfill where stored, synchronized TypeScript updates, and compatibility consideration.
- File endpoints must enforce authorization, size/type limits, safe generated storage keys, and controlled download/preview responses. Never trust the original filename as a path.

## 5. Authentication and authorization

- Validate Supabase JWTs through Spring Security Resource Server. The current authorities are lower-case values such as `admin`, `manager`, `teacher`, and `admissions`.
- Every protected controller or operation needs explicit `@PreAuthorize` rules. UI visibility is not security.
- Use the authenticated subject as identity; do not trust user IDs, roles, or email supplied only by the client.
- `SUPABASE_SERVICE_ROLE_KEY` is server-only. Never return it, embed it in a URL, or copy it into frontend configuration.
- When adding a role or authority, update token/claim mapping, endpoint tests, frontend route/action guards, documentation, and seed/bootstrap logic together.

## 6. Database and migration discipline

- Flyway files live in `src/main/resources/db/migration` and use the next unused `V###__description.sql`. Inspect the directory immediately before choosing a version.
- Never edit, rename, or reorder an applied migration. Add a forward-only migration.
- A schema change must include constraints, indexes for real query paths, safe defaults/backfill for existing rows, and rollback/recovery notes when risky.
- Prefer normalized relational data for operational facts. JSONB is acceptable for versioned builder drafts or provider payloads, not as a shortcut around core relationships.
- Keep seed/demo data separate, deterministic, and idempotent. Production code must work with an empty database and must not synthesize fake records.
- Do not run Flyway clean, drop schemas, truncate user data, or reset local PostgreSQL unless the user explicitly authorizes that exact destructive action.
- Test migrations against PostgreSQL, not only an in-memory substitute.

## 7. Coding and testing standards

- Four-space indentation; standard Java naming; match nearby formatting. Keep methods small and imports readable.
- Tests mirror production packages under `src/test/java`; use `*Tests` class names and behavior-oriented method names.
- Add unit tests for domain rules and integration tests for persistence, security, migrations, module interaction, and non-trivial HTTP contracts.
- Use Testcontainers PostgreSQL when database behavior matters.
- Do not weaken or delete a failing test merely to make CI green.

Commands:

- `docker compose up -d postgres` — start local PostgreSQL.
- `mvn spring-boot:run` — run the API.
- `mvn test` — run tests.
- `mvn verify` — CI-equivalent verification, including module-boundary checks.
- `mvn clean package` — build the executable artifact.
- `docker build -t the-ielts-spells-backend .` — build the image.

Minimum verification:

| Change | Required verification |
| --- | --- |
| Documentation only | Inspect diff; `git diff --check` |
| Domain/service/controller | Focused tests, then `mvn verify` |
| Security or role rules | Authorized + forbidden integration cases, then `mvn verify` |
| Migration/persistence | PostgreSQL integration/migration test, then `mvn verify` |
| API contract used by frontend | Backend verification + frontend `pnpm typecheck` and `pnpm build` + real-flow smoke test |

## 8. Definition of done and handoff

A backend task is complete only when:

- The domain rule is enforced server-side and race-sensitive operations are transactional.
- Endpoint validation, security, success, empty, not-found, conflict, and provider-failure behavior are intentional.
- Migration and `.env.example` changes are documented without real secrets.
- OpenAPI/Swagger represents the implemented contract.
- No fake production data or silent catch-and-success fallback was introduced.
- Required verification passed, or the exact failing command and reason are reported.

Final handoff must list:

1. changed behavior and key files;
2. new/changed endpoints, DTOs, enums, migrations, and environment variables;
3. commands actually run and their results;
4. frontend synchronization completed or still required;
5. known limitations or follow-up work.

Use concise imperative commits, for example `academic: validate enrollment transfer` or `testing: publish normalized reading test`. Keep unrelated changes in separate commits.
