# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/io/routepickapi/**` holds controllers, services, repositories, entities, DTOs, config, and security; follow the controller → service → repository flow.
- Shared configuration sits in `src/main/resources` (`application.yml`, `application-dev.yml`), with Flyway migrations under `src/main/resources/db`.
- Tests mirror production packages in `src/test/java/io/routepickapi/**`.
- Build logic lives in `build.gradle` and `gradle/`; local infra scripts are in `docker-compose.yml`; QueryDSL generates `build/generated` sources.

## Build, Test, and Development Commands
- `./gradlew clean build` compiles, runs the full test suite, and regenerates QueryDSL `Q` types.
- `./gradlew bootRun --args='--spring.profiles.active=dev'` launches the API with local settings.
- `./gradlew test` executes JUnit 5 tests; narrow scope with `--tests io.routepickapi.*`.
- `./gradlew flywayMigrate` applies pending SQL migrations; ensure MySQL is running.
- `docker compose up -d mysql redis adminer` provisions the local database, cache, and Adminer UI defined in `docker-compose.yml`.

## Coding Style & Naming Conventions
- Target Java 21 with 4-space indentation and UTF-8 files.
- Use Spring defaults: `UpperCamelCase` classes, `lowerCamelCase` members, `SCREAMING_SNAKE_CASE` constants, `kebab-case` REST paths.
- Keep DTO validation annotations at the edge; enforce core invariants inside entities/services.
- Prefer constructor injection for services; place reusable helpers in `io.routepickapi.common`.

## Testing Guidelines
- Name tests with the `*Test` suffix under matching packages (`src/test/java/io/routepickapi/...`); use `@SpringBootTest` for flows and slice tests (`@DataJpaTest`, `@WebMvcTest`) for focused checks.
- Mock external integrations; spin up embedded components only when necessary.
- Cover service and repository logic, especially around security and migrations; add regression tests for reported bugs.
- Run `./gradlew test` before pushing and confirm `./gradlew flywayMigrate` succeeds after schema changes.

## Commit & Pull Request Guidelines
- Follow `type(scope): summary`; common types in history include `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.
- Branch from `dev` using `feature/<ticket-or-topic>`; merge through PRs targeting `dev` (hotfixes go directly to `main` when coordinated).
- PRs must describe the change, reference issues, attach test evidence (`./gradlew test`, Swagger screenshots), and call out new env vars or migrations.
- Flag security-sensitive updates and coordinate with maintainers before merging.

## Security & Configuration Tips
- Store secrets in `.env`; keep them out of Git. Match the active profile via environment or `--spring.profiles.active`.
- Update `src/main/java/io/routepickapi/security` when adjusting CORS, JWT, or session rules.
- Verify Flyway migrations against the containers started by `docker compose` before promoting changes.
