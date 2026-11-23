# `api/` — REST controllers

One controller per resource, all under `/api`. A controller reads the request, calls a service,
and returns a DTO. It holds no business rule and no access check (see [`../service/`](../service/)).

| Controller | Routes |
|------------|--------|
| `AuthApiController` | `/api/auth/**`: register, login, logout, refresh, `me` |
| `UserApiController` | user profiles, search |
| `ScenarioApiController` | scenarios: create, list, metadata, storyboard, publish, fork |
| `ScenarioCollaborationApiController` | collaborators and invite links |
| `ScenarioInteractionApiController` | likes and bookmarks |
| `ScenarioTagApiController` | tag suggestions |
| `ThumbnailApiController` | scene images: upload, content, layout, title, order |
| `VignetteApiController` | Vignette Maker scenes saved into a scenario (image + editable drawing) |
| `VignetteSceneApiController` | Vignette Maker drafts of the current user |
| `AudioApiController` | scene and background audios |
| `LanguageApiController`, `LanguageFollowController` | language catalogue, follows |
| `CountryApiController` | country lookup from coordinates (map) |
| `CommunityApiController` | discussions and accreditations |
| `NotificationController` | notifications, live stream (SSE) |
| `AdminApiController` | admin dashboard, users, Glottolog settings |

`GlobalApiExceptionHandler` turns exceptions into JSON errors (`ApiError`) with the right HTTP code.

## Subfolders

- [`dto/`](dto/) — request and response records.
- [`docs/`](docs/) — reusable Swagger annotations for error responses.
- [`security/`](security/) — annotations that document who may call an endpoint.

## Adding an endpoint

1. Add the method to the right controller (or a new `*ApiController`).
2. Put the logic and the access check in a service.
3. Annotate it: one access annotation from `security/`, the error responses from `docs/`, `@Operation`.
4. If it must be reachable without login, add the route to `config/SecurityConfig`.
5. Add a `*WebMvcTest` (HTTP status, security, JSON) in `src/test/java/org/titiplex/api`.
