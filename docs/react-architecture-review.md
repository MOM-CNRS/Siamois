# React main panel — architecture report

**Scope:** `frontend/` (React 18, PrimeReact 10.9, TanStack Query 5, Vite, Vitest), embedded in the JSF shell (`pages/focus.xhtml`).
**Date:** 2026-09-30, branch `feat/field-rules-engine`.
**Size:** 12.7k lines of non-test TS/TSX, 92 test files, 758 tests. `tsc --strict`, ESLint and Vitest run in the Maven `test` phase.

This document describes the architecture as it stands and lists what could still be improved, ranked by expected value. It does not track past work; `git log` does that.

---

## 1. Architecture

```
JSF page (focus.xhtml)
 └─ <div data-contract-version data-panel-kind …>  ──  reactPanelBootstrap.js
                                     │
        window.SiamoisMainPanel.mountFromDataset(el)                     mount.ts
          parseMountOptions(dataset)  — version check, typed options     mountOptions.ts
                                     │
   App.tsx
     ├─ useNavigation()      pure reducer + URL codec + popstate + JSF server sync     navigation/
     ├─ providers            QueryClient · Notify (Toast) · WriteMode · Bridge · EntityNavigation
     ├─ PaneSplit            main pane (never remounted) + optional overview pane
     │    each pane inside a PaneErrorBoundary
     ├─ HomePanel            widgets from every entity's config.home
     ├─ EntityListPanel      ← panels/list/{useEntityListData, useListColumns, EntityTable, …}
     └─ EntityDetailPanel    ← config.detail; one PaneErrorBoundary per tab
                                     │
   entities/registry.ts      Map<EntityKey, EntityTypeConfig>   (6 entity types)
   entities/<type>/          config.tsx + api + types (+ CreateForm, FicheTab, DetailHeader when specific)
   entities/*.ts             shared: createEntityApi, typeCatalog, listApi, scope, routes, countCard
   fields/                   answerType → renderer/display registry, layout, option sources
   rules/                    field-rules engine (framework-free, conformance fixture shared with Java)
   api/                      client (bearer JWT, retry on 401), queryKeys, queryClient, errors
```

### Layers and the rule that holds them

- `panels/`, `fields/`, `components/`, `api/`, `auth/` never import an entity folder; an entity folder never imports another. Enforced by ESLint (`no-restricted-imports`, `error`), so this is a checked property, not a convention.
- `rules/` imports nothing from the app.
- One `QueryClient` for every mount (main, overview). Keys come from `api/queryKeys.ts`; nothing is written as a literal.
- The JSF contract is three artifacts that must move together: `reactPanelMount.xhtml` (attributes), `reactPanelBootstrap.js` (stub), `mountOptions.ts` (parser). `MOUNT_CONTRACT_VERSION` makes a mismatch a visible error instead of a half-working panel.

### Extension points

Adding an entity type is: a folder in `entities/`, a config registered in `mount.ts`, a key in `EntityKey`. The config carries columns, list schema, tabs, chrome, create form, row actions, home widget, duplication, and `typesSegment`. Nothing in `panels/` switches on the entity.

---

## 2. Findings and possible improvements

Ranked by value for the effort. **Bold** items are the ones I would do first.

### 2.1 The entity registry is still string-keyed and untyped

- `registry.ts` is `Map<string, EntityTypeConfig<any, any>>`, with 4 `eslint-disable no-explicit-any`, plus 2 more in `panels/listSiblings.ts`. `getEntityType(key)` returns a config whose row and detail types are `any`; every panel therefore works on `any` data.
- `EntityKey` types the colour/route maps but not the lookup.
- **Improvement:** make the registry a mapped type `{ [K in EntityKey]: EntityTypeConfig<Row[K], Detail[K]> }` and give `getEntityType<K extends EntityKey>` a precise return. Panels stay generic by taking `EntityTypeConfig<unknown, unknown>` through a single, documented widening point instead of six `any`s. Removes the largest remaining source of unchecked code.
- **Effort:** medium (2–3 days). **Risk:** low, the compiler finds every site.

### 2.2 DTOs are hand-written and drift silently from Java

- All `entities/*/types.ts` and `fields/types.ts` (212 lines) are written by hand from the OpenAPI responses. A renamed field on the Java side compiles here and fails at runtime, usually as a blank cell.
- The backend already publishes OpenAPI (`OpenApiRestExceptionHandler`, `*OpenApiService`).
- **Improvement:** generate types with `openapi-typescript` into `src/api/generated/`, commit the output, and add a Maven/CI check that regenerated output equals the committed file. Then narrow the hand-written types to `Pick`/`Omit` of the generated ones. Also gives request-body types (`*CreateBody`) for free.
- **Effort:** medium; the value grows with every new endpoint. Already listed as post-merge.

### 2.3 `fields/` knows the entity list

- `fields/display.tsx` (lines ~100–107) and `fields/optionSources.ts` (lines ~76–82) map resource segments and answer types (`"recording-units"`, `_SPECIMEN`, …) to entity keys and colours. This is the one place a new entity type still requires editing generic code: the layering rule is respected only because these are string keys rather than imports.
- **Improvement:** put `resourceSegment` (`"recording-units"`) and `referenceAnswerTypes` on the entity config and build both tables from the registry at startup. Adding an entity then touches only its own folder.
- **Effort:** small (½ day). Also makes the "answer type → entity" mapping testable against the registry (a test that every `EntityKey` appears).

### 2.4 Five near-identical entity folders

- `container` and `phase` are 75–85-line configs, 15-line `api.ts`, 16–17-line `CreateForm` — the sharing is done. What remains:
  - `project/FicheTab.tsx` (254 lines) and `DetailHeader.tsx` (203) are bespoke and not schema-driven; `recordingUnit/DetailHeader.tsx` (126) duplicates a large part of `project/DetailHeader.tsx` (identifier chip, category, inline error, save).
  - `find/CreateForm.tsx` (153) and `place/CreateForm.tsx` re-implement the project-picker + type + error skeleton that `TypeOnlyCreateForm` already provides.
- **Improvement:** extract `IdentifierChip` and the inline-edit error handling from the two DetailHeaders into `components/`; give `TypeOnlyCreateForm` an `extraFields` slot so find and place stop copying its shell. Do not try to make the project fiche schema-driven until its form is in the backend catalog (see 2.7).
- **Effort:** small–medium.

### 2.5 Type catalogs: one fetch, but four ways to ask for it

- The raw `GET …/<x>-types` is fetched once per path (`typesCatalog(path)`), good. But callers build the path themselves in four places (`typeCatalog.ts`, `useTypeRules.ts`, `recordingUnitTypes.ts`, `projectTypes.ts`) as template strings, and each derived view (`effectiveForm`, `typeRules`, list schema) has its own cache key.
- **Improvement:** a single `useTypesCatalog(segment, scope, select)` hook owning the path and using `select` for each derivation, so there is exactly one key per catalog and the URL is written once. Removes `typeRules`, `effectiveForm`, `recordingUnitTypes`, `projectTypes` as separate keys.
- **Effort:** small–medium. Removes a class of "same data fetched twice" bugs by construction.

### 2.6 Cache policy is uniform, the data is not

- `queryClient.ts` sets only `retry`. Catalogs, forms and type rules change on admin action only, yet use the default `staleTime: 0` and refetch on window focus and on every new mount (each opened overview or tab).
- **Improvement:** per-query `staleTime` (catalogs/forms/rules: `Infinity` or minutes, invalidated by the form builder's save; lists: 0). One `defaultOptions.queries.refetchOnWindowFocus: false` is the cheap first step.
- **Effort:** small. Measurable in the Network tab: fewer `*-types` calls when switching tabs.

### 2.7 The bundle is one 940 KB chunk

- Everything loads on the first page, including the table filter UIs, the cell editor and all six entities.
- **Improvement:** `React.lazy` per entity config (the registry already indirects through configs) and for `CellEditOverlay`/`DuplicateStructureOverlay`. The gain is not measured yet. Needs a `Suspense` fallback per pane, which the new `PaneErrorBoundary` slots already delimit.
- **Effort:** medium. Already listed as post-merge; measure first (e.g. `rollup-plugin-visualizer`).

### 2.8 Remaining large files

`panels/list/EntityTable.tsx` (490), `fields/renderers.tsx` (438), `components/table/CellEditOverlay.tsx` (405), `entities/types.ts` (380), `panels/EntityDetailPanel.tsx` (373), `panels/useRowActions.tsx` (313).

- `entities/types.ts` mixes the config contract, list contract, duplication contract and shared DTOs. Splitting it by contract (`config`, `list`, `detail`, `duplication`) makes each panel import what it uses and makes the contract reviewable.
- `renderers.tsx` holds every field renderer in one file; one file per family (text, number, date, reference, concept) keeps each testable alone and is the natural place for lazy loading.
- `useRowActions` has an `exhaustive-deps` suppression (line ~168); the same rule is suppressed in `useRowRules`, `useTypeRules`, `frozenColumns`. Each suppression is a stale-closure risk; worth revisiting one by one (usually a ref or an event-handler pattern removes it).
- **Effort:** small each; no behavior change.

### 2.9 CSS: one 1 600-line global stylesheet, no cascade layer

- `main-panel.css` is unlayered and shares the page with Bootstrap and the JSF theme. A `@layer` was considered and rejected: the host theme is unlayered, so it would win over every rule regardless of specificity. The alternatives are to layer the *theme* too (the right long-term answer, but a change in the JSF build) or to keep prefixing.
- What remains generic-named and could collide: `.entity-list-panel*`, `.home-panel*`, `.panel-toolbar`, `.validation-status-*`, `.duplicate-structure*` (all React-only) and `.sideview*` / `.panel-splitter-panel-*` (shared with the theme).
- **Improvement:** rename the React-only families to `sia-*` when next touched (do not batch it: each rename must be checked against `docs/theme-class-map.md` and the theme SCSS), and split the file by component next to the `.tsx` it styles (CSS Modules or colocated `.css` imported by the component). The split also lets Vite drop CSS of lazily loaded entities (2.7).
- **Effort:** medium, incremental.

### 2.10 Navigation and the JSF bridge

- Back/Forward no longer reload, but the **focus stack is lost on Back and on F5** and "close focus" then performs a real navigation to `back=`. If users go focus → focus → back often, encode the stack in the URL (`back` becomes a list) — the reducer and codec are already pure and round-trip tested, so this is a contained change.
- The bridge is five named remoteCommands resolved through `window[name]`. A typo is a silent no-op. **Improvement:** the bootstrap should verify at mount that every declared `data-action-*` resolves to a function and report the missing ones once, in the same place as the contract-version error.
- The `MOUNT_CONTRACT_VERSION` is bumped by hand on both sides. A Java test that reads `reactPanelMount.xhtml` and compares against a constant shared through a properties file would make forgetting it a build failure.
- `focus.xhtml` with `data-contract-version` has not been rendered by the real JSF stack since the contract check was added — verify on first startup.

### 2.11 Error handling

- `PaneErrorBoundary` covers render failures and `messageForError` covers API failures, but query errors on *reads* are still shown ad hoc (`Message` in each fiche/list). A shared `QueryErrorView` (message + retry calling `refetch`) would make them uniform and reuse `messageForError`.
- `ApiError` now carries `body` and `path`; nothing reports them. A single `onError` on the `QueryCache`/`MutationCache` is the place to add telemetry (or a console group in dev) once, instead of per call site.
- 5xx retries are capped at 2 by `shouldRetry`; consider surfacing "retrying…" after the first failure to avoid a silent 3–6 s wait.

### 2.12 Accessibility and i18n

- Strings are French literals throughout (`"Échec de la création"`, tab labels, tooltips). The JSF side has i18n bundles; the React bundle has none. Introducing `t()` late is expensive, but the surface is still mostly leaf components. A message catalog keyed by id, initially returning the French literal, lets it be adopted file by file. Already listed as post-merge.
- Keyboard and screen-reader behavior is covered ad hoc (a few `aria-label`s, `role="status"`, `role="alert"`). There is no axe check in tests. **Improvement:** add `vitest-axe` on the three panel kinds and the two overlays — a small test set that catches missing labels and roles cheaply.

### 2.13 Quality tooling

- `frontend/src` is not analyzed by SonarCloud (12.7k lines the gate never sees). Enable it after merge so the new-code window starts clean rather than absorbing the whole migration.
- No coverage measurement or threshold for Vitest. Add `@vitest/coverage-v8` with the lcov report wired to Sonar, and start with a floor at the current value so it can only ratchet up.
- ESLint has no formatting rule set and there is no Prettier: diffs mix style and content. Adopting Prettier once, in its own commit, is cheap and makes later review easier.
- Test style: the suite drives React with `createRoot` + `act` by hand in every file. A tiny `renderInto(container)` helper in `src/test/` would remove ~10 lines of setup per file.

---

## 3. What is solid and should be left alone

- The pure navigation reducer and URL codec (round-trip tested against Java's base64url).
- `rules/` as an isolated module with a shared conformance fixture.
- The layering rule enforced by lint.
- The registry-driven panels: no `switch (entityType)` in `panels/`.
- `PaneSplit`: the main pane's position in the tree is stable, so opening the overview never remounts it.
- Mock harness (`dev/panels`) for visual and perf checks without the backend.

## 4. Suggested order

1. 2.6 (`staleTime`, no focus refetch) and 2.3 (registry-derived tables) — small, no risk.
2. 2.1 (typed registry) then 2.2 (generated DTOs) — they compound: generated DTOs are what the typed registry should be parameterized by.
3. 2.5 (one catalog hook) and 2.4 (remaining duplication).
4. 2.7 + 2.8 (lazy entities, split `renderers.tsx` / `types.ts`) once the above shrinks the surface.
5. 2.13 (Sonar on frontend, coverage) right after the merge; 2.9/2.12 incrementally.
