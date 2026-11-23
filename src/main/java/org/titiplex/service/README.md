# `service/` — business rules

Services hold the logic and are the **only** place where access is checked.

| Area | Services |
|------|----------|
| Access | `ScenarioAccessService`: single source of truth for who may view, edit, publish or delete a scenario |
| Scenarios | `ScenarioService`, `ScenarioForkService`, `ScenarioDeletionService`, `ScenarioHistoryService`, `ScenarioTagService`, `ScenarioInteractionService`, `ScenarioCollaborationService` |
| Media | `ThumbnailService` / `ThumbnailManagementService`, `AudioService` / `AudioManagementService`, `VignetteService`, `VignetteSceneService` |
| Users | `UserService`, `RolesService`, `DbUserDetailsService` (Spring Security login) |
| Languages | `LanguageService`, `LanguageFollowService`, `CountryGeoService` |
| Community | `CommunityService` (discussions, accreditation requests and grants) |
| Notifications | `NotificationService` (what to notify), `NotificationStream` (live delivery over SSE) |
| Glottolog | `GlottologAdminService`, `GlottologPythonBootstrapService`, `GlottologLanguageSnapshot` |

**`*ManagementService` vs `*Service`:** the management service is called by the controller; it checks
access, calls the plain service that does the work, and records the history entry.

## Subfolders

- [`storage/`](storage/) — uploaded files on disk.
- [`geo/`](geo/) — country shapes for the languages map.
