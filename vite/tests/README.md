# Frontend tests

Run from `vite/` (`npm install` first):

| Command | Runs |
|---------|------|
| `npm run test:run` | unit tests once (Vitest) |
| `npm run test` | unit tests in watch mode |
| `npm run test:coverage` | unit tests with a coverage report |
| `npm run e2e` | end-to-end tests (Playwright) |
| `npm run e2e:ui` | end-to-end tests in the Playwright UI |

The first Playwright run may ask to install the browsers: `npx playwright install`.

## Layout

| Folder | Content |
|--------|---------|
| `unit/` | Vitest tests, mirroring `src/`: `api/`, `components/`, `composables/`, `router/`, `utils/`, `views/` |
| `e2e/` | Playwright scenarios: login guard, creation privacy, home and reader, scenario playback, studio flow |
| `helpers/` | Shared test tools: `mountWithRouter`, DOM helpers, fake API responses, component stubs |
| `setup.js` | Global Vitest setup |

## How they run

- **Unit tests** run in a simulated browser (jsdom). API calls are mocked: no backend needed.
  Composables take their dependencies as options (e.g. `uploadScenarioThumbnail`), so tests pass `vi.fn()`.
- **End-to-end tests** build the app and serve it with `npm run preview` on http://127.0.0.1:4173
  (see `playwright.config.js`). The backend is not started: each scenario intercepts the API calls
  with `page.route(...)`.

## Writing a test

- Put the test next to the matching folder in `unit/` and name it `<file>.test.js`.
- Test what the user sees or what is sent to the API, not internal details.
- A new user flow (several pages, navigation) belongs in `e2e/`.
