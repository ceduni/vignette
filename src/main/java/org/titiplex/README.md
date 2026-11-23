# Backend (`org.titiplex`)

Spring Boot REST API. Every request goes through the same layers:

```text
HTTP ─► api/ (controller) ─► service/ (rules, access checks) ─► persistence/repo ─► PostgreSQL
                                   └──► service/storage (uploaded files on disk)
```

| Folder | Role |
|--------|------|
| [`api/`](api/) | REST controllers: HTTP in, DTOs out. No business rules. |
| [`service/`](service/) | Business rules, access checks, history, notifications. |
| [`persistence/`](persistence/) | JPA entities and Spring Data repositories. |
| [`config/`](config/) | Spring configuration: security, JWT, OpenAPI, typed properties. |
| [`bootstrap/`](bootstrap/) | Startup tasks: admin account, Glottolog first import. |
| [`util/`](util/) | Small stateless helpers shared by every layer. |

`Application.java` is the entry point. The database schema lives in
[`src/main/resources/db/migration`](../../../resources/db/migration/).

## Conventions

- Controllers stay thin: they read the request, call **one** service method, map the result to a DTO.
- Access rules are enforced in services (mainly `ScenarioAccessService`), never in controllers.
  The annotations in `api/security` only document them in Swagger.
- Errors are exceptions mapped to HTTP codes by `api/GlobalApiExceptionHandler`:

  | Exception | HTTP |
  |-----------|------|
  | `IllegalArgumentException` | 400 |
  | `InsufficientAuthenticationException` | 401 |
  | `AccessDeniedException` | 403 |
  | `NoSuchElementException` | 404 |
  | `IllegalStateException` | 409 |
