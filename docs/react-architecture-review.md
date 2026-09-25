# React main panel — architecture review

**Scope:** `frontend/` (React 18 + PrimeReact 10 + TanStack Query 5, Vite, Vitest), as embedded in the JSF shell (`pages/focus.xhtml`).
**Branch:** `feat/main-panel-react-migration`, 2026-09-25.
**Size:** ~10k lines of non-test TS/TSX, 70 test files, 559 tests (all green).
**Tone:** deliberately critical. The "what's good" section is short because it's the part that needs no action, not because there's little of it.

---

## 1. How it's built

```
JSF page (focus.xhtml)
 ├─ <head>: PrimeFaces siamois theme.css, bootstrap 5.3, settings.css
 └─ body: <link main-panel.css>  <script main-panel.js>  reactPanelBootstrap.js
                                     │
            window.SiamoisMainPanel.mount(el, MountOptions)   (mount.ts)
                                     │
   App.tsx ── owns: view (home|list|detail), overview pane, focus-mode stack, URL (pushState)
     │         providers: QueryClient · WriteMode (FlowBean.isWriteMode) · Bridge (JSF remoteCommands)
     ├─ HomePanel         ← widgets from every registered entity's config.home
     ├─ EntityListPanel   ← config.list  (columns, schema, filters, create form, row actions)
     └─ EntityDetailPanel ← config.detail (tabs, header, chrome)
                                     │
   entities/registry.ts  Map<key, EntityTypeConfig>   ← registered in mount.ts, 6 entity types
   entities/<type>/      api.ts · types.ts · config.tsx · columns · FicheTab · CreateForm · DetailHeader · form.ts · routes · homeWidgets
   fields/               field-renderer registry (answerType → display/edit), FieldEditCell, CellEditOverlay
   api/client.ts         fetch wrapper: bearer JWT, one retry on 401
   auth/                 JSF session → JWT exchange (POST /api/auth/session-token, CSRF), in-memory token
```

The central idea is **generic panels driven by a per-entity config registry** (`EntityTypeConfig`, `entities/types.ts`). Adding an entity type means writing one config module plus one `registerEntityType` call in `mount.ts:24-31`.

---

## 2. What's good

- **The registry pattern is the right shape** for a migration where N entity types share three screens. `PanelContent` (`App.tsx:85`) has no per-entity branching, and the list and detail panels only talk to `EntityTypeConfig`.
- **Server-driven forms.** Fiches render the backend's layout JSON and field catalogue (`FicheTab` + `fields/registry.ts`), with renderers keyed by `answerType`. That's the correct way to reach parity with a configurable JSF form engine without hard-coding forms twice.
- **The auth bridge is minimal and correct.** A single-flight token fetch (`auth/sessionAuth.ts:getAccessToken`), an in-memory token (never `localStorage`), a 30 s refresh skew, and one retry on 401 (`api/client.ts:31`). The backend fix that stops the JWT chain touching the JSF session is also well reasoned.
- **TanStack Query** handles server state instead of a hand-rolled store, and `useMutation` is used consistently (26 call sites).
- **Test density is high**, and it covers behaviour (panels, renderers, table state, the virtual list, API mappers) rather than snapshots. `tsc --strict` is clean.
- **Cheap performance wins are already in:** the `fields=` projection only asks for visible columns, labels are resolved in batch server-side, and the list is virtualised (`panels/useVirtualList.ts`).

---

## 3. Honest criticism

### 3.1 Duplication across entity modules: the registry scales the *panels*, not the *entities*
Each of the 6 entity folders contains its own `api.ts`, `types.ts`, `FicheTab.tsx`, `CreateForm.tsx`, `DetailHeader.tsx`, `form.ts` and `homeWidgets.tsx`. Measured with `diff`:

| File | container vs phase: lines differing / total |
|---|---|
| `FicheTab.tsx` | 65 / 287 (≈ **77 % identical**) |
| `CreateForm.tsx` | 28 / 190 (≈ **85 %**) |
| `columns.tsx` | 19 / 51 |
| `api.ts` | 41 / 99 |
| find vs phase `FicheTab.tsx` | 62 / 294 (≈ **79 %**) |

The "generic" architecture pushed the variation into config, but each config then re-implements the same fiche, create form and API adapter by copy-paste. The 7th entity type will cost roughly another 1,000 lines, most of them copied, and every bug fix in a fiche has to be applied six times.

**Fix:**
- One generic `SchemaFicheTab`, parameterised by `{ loadForm(entity), patch(id, answers), entityType }`.
- One `createEntityApi(collectionPath, mappers)` factory.
- One `SchemaCreateForm`.

Entity folders then keep only what really differs: columns, header chips, and odd cases like the project places tab.

### 3.2 `App.tsx` is a hand-rolled router + state machine + layout in one component
`App.tsx` (519 lines, 8 `useRef` + 5 `useState`) owns:
- navigation state
- the overview pane
- the focus-mode stack
- URL encoding (`base64url`/`focusUrl`)
- history `pushState`
- syncing FlowBean through the bridge
- the splitter layout

The refs mirror state purely to keep callbacks stable (`viewRef`, `overviewRef`, `mainPathRef`, `focusStackRef`, `serverOverviewRef`…), which is a sign that this should be a reducer. `popstate` falls back to `window.location.reload()` (`App.tsx:363`), so browser Back is a full JSF round-trip.

**Fix:** extract a `useNavigationStore` built on `useReducer`, with pure `encodeUrl`/`decodeUrl` functions. Decoding is the missing half: once `/focus/<b64>?s=<b64>` can be parsed back into a state, `popstate` becomes a dispatch instead of a reload, and deep links stop depending on JSF. A tiny router is fine; a library isn't needed.

### 3.3 Separation of concerns: generic layers import specific ones
- `panels/EntityListPanel.tsx:17` imports `searchCreatableProjects` from `entities/project/api`. So the generic list panel knows about projects, and the dependency points the wrong way.
- `entities/{phase,container,find,place,recordingUnit}/FicheTab.tsx` import `parseLayout`/`toGridClass` from `entities/project/form`. Generic form-layout code lives inside the *project* entity folder.
- `EntityListPanel.tsx` (771 lines, 181 comment lines) mixes several jobs:
  - query orchestration
  - column-picker state
  - filters
  - cell-edit overlay coordination
  - the create dialog
  - row actions
  - DOM hit-testing (`target.closest("td")`)

**Fix:**
- Move `form.ts` layout parsing to `fields/layout.ts`.
- Pass "creatable projects" through `config.list` (or a hook injected by config).
- Split `EntityListPanel` into `useEntityList` (data), `useColumnSelection`, and a presentational `<EntityTable>`.

### 3.4 Type safety stops at the network boundary
- All API types are **hand-written** (`entities/*/types.ts`, `fields/types.ts`). The backend already has springdoc, yet nothing generates TS from `/v3/api-docs`. Every DTO change on the Java side is a silent runtime break. The API change report (`docs/api-changes-vs-main.md`) lists over 40 such shape changes on this branch alone.
- The registry is `Map<string, EntityTypeConfig<any, any>>` (`entities/registry.ts`), so `getEntityType(key)` returns `any`-typed rows to the panels. Entity keys are free strings (`"recordingUnit"`, `"project"`…) scattered across modules.
- Responses are cast (`JSON.parse(text) as T`, `api/client.ts`) with no runtime validation.

**Fix:**
- Generate types with `openapi-typescript` in the Maven build, and fail CI on drift.
- Make the registry keyed by a string-literal union.
- Optionally validate at the adapter boundary with zod, just for detail responses.

### 3.5 Server-state hygiene
- **Query keys are ad-hoc string arrays** in ~30 places (`["entity-detail", …]`, `["recording-unit-effective-form", …]`, `["phase-effective-form", …]`), with no key factory.
- Invalidation is very broad: `invalidateQueries({ queryKey: ["entity-detail"] })` and `["entity-list"]` wipe *every* entity type after a single edit (e.g. `DetailHeader`, the create forms).
- The `QueryClient` is created with defaults (`App.tsx:14`): 0 ms `staleTime`, 3 retries, refetch on focus. That's aggressive for form catalogues that effectively never change during a session.

**Fix:**
- Add a `queryKeys` module per entity.
- Invalidate `[entity, id]` precisely, or use `setQueryData` from the PATCH response, which already returns the updated resource.
- Set `staleTime: Infinity` for type and form catalogues, and `retry: (n, e) => e.status >= 500 && n < 2`.

### 3.6 CSS isolation: the bundle restyles the host page
Before this branch's theming pass, `mount.ts` imported the Lara theme, `primereact.min.css`, PrimeIcons and **PrimeFlex**. It now imports `src/styles/bundle.ts`: PrimeIcons and `main-panel.css`, with Lara and PrimeFlex gone. The result is loaded into the JSF page, *after* the PrimeFaces theme and Bootstrap (`focus.xhtml:362`). Consequences:
- *(Resolved on this branch.)* PrimeFlex's global utilities (`.col-6`, `.grid`, `.flex`, `.hidden`, `.p-2`, …) collided with Bootstrap's same-named classes across the *whole* page. The fiche grid, the only part used, is now `main-panel.css`'s own `sia-grid`/`sia-col-N`, and PrimeFlex is no longer a dependency (bundle CSS 373 KB → 27 KB).
- *(Resolved on this branch.)* Lara's `:root` variables and `.p-*` rules used to fight the Siamois theme. The host theme now skins PrimeReact itself, through `theme-base/_primereact.scss`.
- `main-panel.css` has 935 lines of global selectors. Some are scoped by a component-ish prefix (`entity-list-panel-*`), but they share one namespace with the JSF app.
- There are 66 inline `style={{…}}` objects. Some are documented workarounds (`App.tsx` forces Splitter `display:flex` because of `@layer primereact` ordering), but most are just layout.

**Fix:**
- ~~Drop Lara and use the host theme~~ (done).
- ~~Scope or drop PrimeFlex~~ (done: replaced by `sia-grid`/`sia-col-N`).
- Wrap bundle CSS in `@layer siamois-react` so precedence is explicit.
- Move inline layout styles into classes.

### 3.7 JSF coupling is wide and implicit
`MountOptions` (`mountOptions.ts`) carries:
- base path
- CSRF
- write mode
- two chromes
- organisation ids
- a `goBackUrl`
- a `bridge` of PrimeFaces `remoteCommand`s (`setOverview`, `closeOverview`, `openProjectSettings`)

All of this is read once at mount. Write mode can't change without a remount, and FlowBean's state is synced by fire-and-forget calls with no acknowledgement (`App.tsx openOverview`). The mount/unmount lifecycle relies on the JSF page never re-rendering the container through AJAX. `writeMode.tsx` documents this, but nothing enforces it.

Short term, that's acceptable for a strangler migration. It is the part that will hurt most if both apps coexist for long.

**Fix:**
- Write the bridge contract down as a typed, versioned interface, and add one test that checks the XHTML-generated options object against it.
- Treat the bridge as an event bus (`CustomEvent`s on the mount element) rather than callbacks, so either side can be replaced independently.

### 3.8 Error handling and UX resilience
- There's **no React error boundary**. A render exception in one fiche field blanks the whole panel, including the other pane.
- Error messages are per-component strings (`"Échec du favori"`, `"Impossible de charger…"`) rendered in `<Message>`. There is no shared toast or notification channel and no mapping of `ApiError.status` to user messages. A `403` and a `500` look the same.
- `ApiError` keeps only `status` and `message`. The backend's `ResponseStatusException` body (reason, path) is partly lost.

**Fix:**
- Add one `<ErrorBoundary>` per pane, plus one per fiche panel.
- Use a `useNotify()` backed by a PrimeReact `Toast` at App level.
- Add a status → message map.

### 3.9 i18n, accessibility, conventions
- **All UI strings are hard-coded French.** The JSF app has `langBean` and resource bundles; the React bundle has no i18n layer, so a language switch in JSF won't translate the React panel. Even a trivial `t()` over a JSON catalogue generated from the existing `.properties` files would do.
- Accessibility is mostly left to PrimeReact (29 `aria-*` attributes hand-written). Clickable table cells and custom overlays (`CellEditOverlay`) need keyboard paths and focus return. Worth an axe pass.
- **Comment density is very high:** ~2,200 comment lines for ~10k lines of code. Many cite "plan §x phase y", or explain *history* ("the bug this fixes") rather than intent. That's useful during the migration, but it will rot. Move the rationale to ADRs and PR descriptions, and keep code comments to the non-obvious *why*.
- **Tooling gaps:**
  - `npm run lint` fails: ESLint 9 is installed, but there's no `eslint.config.js`. There's no Prettier config either.
  - Two unhandled errors leak out of `CreateForm.test.tsx` (PrimeReact AutoComplete focus in jsdom). Vitest reports them but still passes.

### 3.10 Scalability outlook
| Axis | Today | At 12+ entity types / 2+ teams |
|---|---|---|
| Panels | Generic, good | Holds |
| Entity modules | ~1k lines each, ~80 % copied | Linear growth of duplicate code, divergent bugs |
| Types | Hand-written | Drift with every backend change |
| Navigation | One component, reload on Back | Harder to add routes (settings, search pages) |
| Bundle | Single chunk, everything eager | Grows with every entity; no code-splitting per entity type (`registerEntityType` could take a lazy `import()`) |
| Styling | Global CSS + Lara vs host theme | Collisions multiply as more JSF screens sit next to React ones |

### 3.11 Evidence from the theming pass (2026-09-25)

These are concrete defects found while aligning the React panel with the JSF theme. They show where the current structure makes bugs easy to write.

- **Colour scheme tied to what JSF mounted.** The per-entity colour classes (`siamois-panel action-unit-panel …`) lived only on the JSF wrapper around the mount. After a client-side navigation to another entity type, and in the overview pane, React kept rendering under the *mounted* entity's colours. Fixed by `EntityTypeConfig.panelClass` plus `App.tsx paneClassName`. It's the same root cause as §3.7: state duplicated between JSF and React with no single owner.
- **`@layer primereact` vs Bootstrap.** PrimeReact 10 injects its structural CSS inside a cascade layer, and `primereact.min.css` is an empty stub. Bootstrap's unlayered reboot therefore overrides PrimeReact's own resets (`ul` padding in menus, for example). Nothing in the bundle documented or tested this. It's now handled in the shared theme.
- **Font never loaded.** Sass interpolated the JSF expression in `_fonts.scss`, so production `theme.css` pointed at `url("resource [fonts:…]")`. It's a build-level bug, but it shows there's no visual check of the compiled theme. The new harness pages (`frontend/dev/theme`, `frontend/dev/panels`) are a first step. A Playwright screenshot test over them would make this permanent.
- **A default that the API rejects.** `/recording-units/{id}/mobiliers` defaults `sort=creationTime:desc`, but the code path used as soon as a field filter is present rejects `creationTime` with a 400. That's the typing gap of §3.4 on the backend side: a shared, generated contract for sortable/filterable fields per list would make this impossible.
- **Dead styling hooks.** 46 of the 112 custom class names the JSX emits have no rule anywhere. Only 16 are shared with the JSF markup, although the theme's app-level rules are all keyed on JSF class names. Reusing the JSF names, which is now documented in `docs/theme-class-map.md`, is what lets one theme serve both apps.

---

## 4. Prioritised recommendations

| # | Action | Value | Effort |
|---|---|---|---|
| 1 | Generate API types from OpenAPI (`openapi-typescript`) and wire into the build | High: kills a whole class of silent breaks | S |
| 2 | Extract `SchemaFicheTab` / `SchemaCreateForm` / `createEntityApi`, and delete the per-entity copies | High: roughly −3k lines, one place to fix bugs | M |
| 3 | ~~Drop Lara, host-theme parity~~ (**done on this branch**, see `docs/theme-class-map.md`); PrimeFlex dropped too; still to do: add a screenshot test over the dev harnesses | High: visual consistency, no page-wide collisions | S–M |
| 4 | Query-key factory, precise invalidation, `staleTime` for catalogues | Medium: fewer refetch storms, snappier UI | S |
| 5 | Error boundaries per pane plus a central notifier | Medium | S |
| 6 | Move generic code out of `entities/project` (`form.ts` → `fields/layout.ts`); remove the `panels → entities/project` import | Medium: dependency direction | S |
| 7 | Split `App.tsx` into a navigation reducer with URL decode (Back without reload) | Medium | M |
| 8 | Split `EntityListPanel` into hooks + a presentational table | Medium | M |
| 9 | Add `eslint.config.js` (typescript-eslint, react-hooks, jsx-a11y) and Prettier | Medium: currently no lint at all | S |
| 10 | i18n layer seeded from the JSF bundles | Medium, higher if non-French users exist | M |
| 11 | Lazy-load entity modules | Low now, grows later | S |
| 12 | Trim migration-history comments into ADRs | Low | S |

**Bottom line:** the panel-level architecture is sound and above average for a JSF strangler migration. The debt sits one level down:
- per-entity copy-paste,
- untyped API contracts,
- global CSS,
- a monolithic `App`.

All four are cheap to fix *now*, with six entity types, and expensive at twelve.
