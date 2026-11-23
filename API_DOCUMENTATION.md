# API documentation

The backend documents its API automatically with **springdoc-openapi**. The same documentation is
published as a static page on **GitHub Pages**.

## Local documentation

Start the backend (`mvn spring-boot:run -Dspring-boot.run.profiles=dev`), then open:

| Page | URL |
|------|-----|
| Swagger UI | http://localhost:8081/api/docs |
| OpenAPI JSON | http://localhost:8081/api/docs/openapi |

springdoc scans the controllers at each start, so the documentation always matches the code.

## Documenting an endpoint

Each endpoint should have:

- `@Operation(summary, description)`;
- **one** access annotation from `api/security` (`@PublicOperation`, `@AuthenticatedOperation`,
  `@OwnerOrAdminOperation`, `@AdminOperation`…), which shows who may call it;
- the error responses it can return, from `api/docs` (`@BadRequestResponse`, `@NotFoundResponse`…);
- `@Schema` descriptions on the fields of its DTOs.

See [`src/main/java/org/titiplex/api/README.md`](src/main/java/org/titiplex/api/README.md).

## Publishing on GitHub Pages

Workflow: [`.github/workflows/publish-api-docs.yml`](.github/workflows/publish-api-docs.yml).

- **Runs on:** every push to `master`, or manually (`workflow_dispatch`).
- **Steps:** builds and starts the API, exports `/api/docs/openapi` to `docs/openapi.json`, then
  publishes the `docs/` folder.
- The published page is `docs/index.html` (Redoc), which reads `./openapi.json`.

### One-time setup on GitHub

1. **Settings → Pages → Source:** select **GitHub Actions**.
2. Run the **Publish API docs** workflow once.

The documentation is then available at the repository's Pages URL
(https://titiplex.github.io/vignette/).
