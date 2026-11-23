# `service/storage/` — uploaded files

`FileStorageService` writes uploads under `APP_STORAGE_ROOT` (default `./data/storage`):

```text
thumbnails/scenario-<id>/<ab>/<cd>/<sha256>.<ext>
audios/scenario-<id>/thumbnail-<id>/…   (scene audio)
audios/scenario-<id>/background/…       (background audio)
```

- Files are **content-addressed** (named by their SHA-256), so the same file is stored once.
- Only known image and audio types are accepted. **SVG** is accepted for scene images only and is
  cleaned by `util/SvgSanitizer` before being stored. Plain XML is refused.
- Paths are resolved inside the root only (no `../`).
- `deleteAfterCommit` removes a file only if the database transaction succeeds.

`StoredFile` describes a saved file; `MediaContent` is what controllers stream back.
Files are not in the database: back up `APP_STORAGE_ROOT` separately.
