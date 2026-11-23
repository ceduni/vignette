# `config/` — Spring configuration

| Class | Role |
|-------|------|
| `SecurityConfig` | Which routes are public, CSRF (cookie → `X-XSRF-TOKEN` header), JWT bearer tokens. |
| `JwtConfig` | Key pair, JWT encoder and decoder (secret from `APP_JWT_SECRET`). |
| `AuthManagerConfig` | `AuthenticationManager` used by login. |
| `OpenApiConfig` | Swagger/OpenAPI setup, sorted tags and paths. |
| `DataRestConfig` | Registers Spring Data web settings (JSON format of paged results) that Spring Data REST would otherwise disable. |
| `StorageProperties` | `app.storage.*`: where uploaded files are stored. |
| `GlottologProperties` | `app.glottolog.*`: Python executable, version, update frequency bounds. |

Values come from `src/main/resources/application*.properties` and environment variables
(see "Configuration" in the root README).
