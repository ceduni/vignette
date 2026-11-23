# `persistence/model/` — JPA entities

| Domain | Entities |
|--------|----------|
| Users | `Author` (base), `User` (joined inheritance, table `user_`), `Role` |
| Scenarios | `Scenario`, `ScenarioTag`, `ScenarioLike`, `ScenarioBookmark`, `ScenarioHistoryEntry` |
| Collaboration | `ScenarioCollaborator`, `ScenarioInviteLink` |
| Media | `Thumbnail` (scene image, may hold a Vignette Maker drawing), `Audio` |
| Vignette Maker | `VignetteScene` (drafts) |
| Languages | `Language`, `LanguageFollow` |
| Community (`community` schema) | `DiscussionMessage`, `AccreditationRequest`, `CommunityAccreditation` |
| Notifications | `Notification` |
| Glottolog import | `GlottologAdminSettings`, `GlottologPipelineState`, `GlottologUpdateHistory`, `GlottologUpdateRequest`, `GlottologNotification` |

The remaining files are enums (`*Status`, `*Type`, `*Role`…) and value records such as
`AccreditationScope`.

**Changing an entity always needs a migration** (see `db/migration/README.md`). An enum stored as a
string has a `CHECK` constraint listing its values, so a new constant needs a migration too.
