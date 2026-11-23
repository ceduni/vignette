# Backend tests

```bash
mvn clean test                                  # all tests (Docker must be running)
mvn test -Dtest=VignetteServiceTest             # one class
mvn test -Dtest='Thumbnail*Test'                # a pattern
```

Reports: `target/surefire-reports/`.

## Kinds of tests

| Kind | Naming | What it checks | Needs Docker |
|------|--------|----------------|--------------|
| Unit | `*Test` | One class, dependencies mocked with Mockito | no |
| Web layer | `*WebMvcTest` | HTTP status, security rules, JSON of one controller (`@WebMvcTest` + `SecurityConfig`) | no |
| Integration | `*IntegrationTest` | Whole application on a real PostgreSQL | **yes** |

The integration tests (`ApiFlowIntegrationTest`, `ApiFailureFlowIntegrationTest`,
`OpenApiDocsIntegrationTest`) use `PostgresTestContainerConfig`: Testcontainers starts a throwaway
`postgres:17-alpine`, Flyway applies the migrations, and Hibernate checks that the entities match the
schema. Without Docker they fail with `Failed to load ApplicationContext`; the other tests still run.

## Layout

The folders mirror `src/main/java/org/titiplex`: the test of `service/UserService` is
`service/UserServiceTest`.

| Folder | Content |
|--------|---------|
| `api/` | Controller tests (`*ControllerTest`, `*WebMvcTest`) and the API integration flows |
| `service/` | Service unit tests, `storage/` for file storage |
| `persistence/` | Entity and enum tests |
| `config/` | OpenAPI documentation integration test |
| `bootstrap/` | Startup tasks |
| `util/` | Helpers (`Text`, `AuthUtils`, `SvgSanitizer`) |
| `src/test/resources/glottolog/` | Sample Glottolog CSV files |

## Writing a test

- Every new service method or endpoint gets a test; every bug fix gets a test that failed before it.
- Services: mock the repositories and other services, assert the result **and** the access check
  (e.g. `verify(access).assertCanEditScenario(...)`).
- Endpoints: in the `*WebMvcTest`, cover at least the success case, `401` without login and the
  main error codes. Use `.with(csrf())` for `POST`/`PUT`/`PATCH`/`DELETE`.
- Schema changes are covered by the integration tests: run them before pushing a migration.
