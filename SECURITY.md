# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| `master` branch (1.0.x) | :white_check_mark: |
| Older commits and forks | :x: |

## Reporting a vulnerability

**Please do not describe an exploitable vulnerability in a public issue.**

1. Email **titouan.johanny@umontreal.ca** with:
   - what is affected (endpoint, page, file),
   - the steps to reproduce it,
   - the impact you expect (data read, account takeover, code execution…).
2. You will get an answer whether the report is accepted or not.
3. Once a fix is released, the issue can be discussed publicly.

For minor problems that cannot be exploited (outdated dependency without a known attack path,
missing header…), a regular issue or a pull request is fine. Document the change in the pull
request and in the code; a maintainer will review it.

Improvements to this policy are also welcome through pull requests.

## What the project already does

Useful to know before reporting, and to keep in mind when changing the code:

| Area | Protection | Where |
|------|------------|-------|
| Authentication | JWT bearer tokens; CSRF token (cookie → `X-XSRF-TOKEN`) for cookie sessions | `config/SecurityConfig`, `config/JwtConfig` |
| Authorization | Who may view or edit a scenario is checked in one place | `service/ScenarioAccessService` |
| Accounts | Usernames and emails unique regardless of case; hashed passwords | `service/UserService` |
| Uploads | Allow-list of image and audio types; files named by SHA-256, kept inside the storage root | `service/storage/FileStorageService` |
| SVG | Scripts, event handlers, external links and DTDs removed before storage | `util/SvgSanitizer` |
| Serving images | `Content-Security-Policy: sandbox` on image content | `api/ThumbnailApiController` |
| Database | Schema changed by Flyway only; Supabase Data API must stay off (see the README) | `src/main/resources/db/migration` |
| Secrets | `APP_JWT_SECRET`, database and SMTP passwords come from environment variables or an untracked `.env` | `application*.properties` |
