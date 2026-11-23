# `util/` — shared helpers

Stateless `final` classes with static methods, usable from any layer.

| Class | Role |
|-------|------|
| `Text` | Clean user text: `blankToNull`, `trimOrDefault`, `requireMaxLength`, `optional`, `firstNonBlank`, `trimmedOrEmpty`. |
| `AuthUtils` | Authentication checks: `isAuthenticated`, `requireAuthenticated`, `authenticatedUsername`, `isAdmin`, `ROLE_ADMIN`. Use with `import static`. |
| `SvgSanitizer` | Turns an uploaded SVG into drawing-only SVG: no scripts, event handlers, external links or DTD. |

Put a helper here only if it has no Spring dependency and is (or will be) used in more than one place.
Each class has a test in `src/test/java/org/titiplex/util`.
