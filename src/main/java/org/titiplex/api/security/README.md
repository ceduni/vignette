# `api/security/` — access annotations (documentation only)

Each endpoint carries **one** of these annotations, which shows in Swagger who may call it:

| Annotation | Meaning |
|------------|---------|
| `@PublicOperation` | anyone, logged in or not |
| `@AuthenticatedOperation` | any logged-in user |
| `@UserOperation` | logged-in users with the `USER` role |
| `@OwnerOrAdminOperation(resource, param)` | owner/editor of the resource, or an admin |
| `@AdminOperation` | admins only |

`ApiAccess` / `ApiAccessLevel` are the generic form behind these annotations.
`ProtectedResource` names what `@OwnerOrAdminOperation` protects (scenario, thumbnail, audio),
and `param` is the path variable holding its id.
`OpenApiAccessCustomizer` and `OwnerOrAdminOpenApiCustomizer` write this into the OpenAPI spec.

> These annotations **do not enforce anything.** Login is required by `config/SecurityConfig`,
> and ownership is checked in services (`ScenarioAccessService`). Keep both in sync.
