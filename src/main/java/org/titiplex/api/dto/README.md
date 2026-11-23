# `api/dto/` — request and response bodies

Java `record`s exchanged with the frontend. Entities are never returned directly.

- `*Request` / `*RequestBody`: JSON sent by the client.
- `*Response` / `*Dto` / `*RowDto`: JSON returned. Most have a static `from(entity)` factory.
- `ApiError`: body of every error response.
- `AuthUserPrincipal`: the logged-in user as seen by controllers.

Describe each field with `@Schema` so it appears in Swagger.
