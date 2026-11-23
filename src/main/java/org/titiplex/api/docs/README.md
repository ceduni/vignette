# `api/docs/` — shared error responses for Swagger

Meta-annotations that add a documented error response (body `ApiError`) to an endpoint, so each
controller does not repeat the same `@ApiResponse` block.

| Annotation | HTTP |
|------------|------|
| `@BadRequestResponse` | 400 |
| `@UnauthorizedResponse` | 401 |
| `@ForbiddenResponse` | 403 |
| `@NotFoundResponse` | 404 |
| `@ConflictResponse` | 409 |

They only affect the documentation, not the behaviour.
