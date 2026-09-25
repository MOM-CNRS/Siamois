# React main panel — architecture review

**Scope:** `frontend/` (React 18 + PrimeReact 10 + TanStack Query 5, Vite, Vitest), as embedded in the JSF shell (`pages/focus.xhtml`).
**Branch:** `feat/main-panel-react-migration`, 2026-09-25. First written at `c47abeea1`, then updated at `e31e1de8d` after the table/actions/styles commits and the JSF dead-code removal (see §3.12 for what changed).
**Size:** ~10.8k lines of non-test TS/TSX, 73 test files, 587 tests (all green), `tsc --strict` clean.
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
- **TanStack Query** handles server state instead of a hand-rolled store, and `useMutation` is used consistently for writes.
- **Test density is high**, and it covers behaviour (panels, renderers, table state, the virtual list, API mappers) rather than snapshots. `tsc --strict` is clean.
- **Cheap performance wins are already in:** the `fields=` projection only asks for visible columns, labels are resolved in batch server-side, and the list is virtualised (`panels/useVirtualList.ts`). Showing a column no longer resets the list: only that column is fetched for the rows already loaded (see §3.12 for the cost of this).

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

*(Update.)* The organization-wide column catalogues show both the good path and the trap. Phase, container and find went through the shared `typeCatalog.ts` (`typeCatalogPath`), so each needed only a one-line change. Recording unit has its own `recordingUnitTypes.ts`, so it got a separate `getOrganizationRecordingUnitTypes` that repeats the project/organization branch by hand. Nothing in the fiche/create-form duplication above has changed.

### 3.2 `App.tsx` is a hand-rolled router + state machine + layout in one component
`App.tsx` (532 lines, 8 `useRef` + 5 `useState`) owns:
- navigation state
- the overview pane
- the focus-mode stack
- URL encoding (`base64url`/`focusUrl`)
- history `pushState`
- syncing FlowBean through the bridge
- the splitter layout

The refs mirror state purely to keep callbacks stable (`viewRef`, `overviewRef`, `mainPathRef`, `focusStackRef`, `serverOverviewRef`…), which is a sign that this should be a reducer. `popstate` falls back to `window.location.reload()` (`App.tsx:374`), so browser Back is a full JSF round-trip. Removing `template.js` took away the competing jQuery `popstate` handler, so React is now the only owner of history. That makes the decode step below easier to add, not less needed.

`navigate` is now also published through a context (`panels/entityNavigation.tsx`) so that a field's reference chip can open a fiche. That's the right fix for prop threading. It's also the fourth context `App` provides (QueryClient, WriteMode, Bridge, EntityNavigation), which is one more reason to give navigation its own store.

**Fix:** extract a `useNavigationStore` built on `useReducer`, with pure `encodeUrl`/`decodeUrl` functions. Decoding is the missing half: once `/focus/<b64>?s=<b64>` can be parsed back into a state, `popstate` becomes a dispatch instead of a reload, and deep links stop depending on JSF. A tiny router is fine; a library isn't needed.

### 3.3 Separation of concerns: generic layers import specific ones
- `panels/EntityListPanel.tsx:17` imports `searchCreatableProjects` from `entities/project/api`. So the generic list panel knows about projects, and the dependency points the wrong way.
- `entities/{phase,container,find,place,recordingUnit}/FicheTab.tsx` import `parseLayout`/`toGridClass` from `entities/project/form`. Generic form-layout code lives inside the *project* entity folder.
- `EntityListPanel.tsx` has grown from 771 to **871 lines** since the first review. It mixes several jobs:
  - query orchestration
  - column-picker state
  - filters
  - cell-edit overlay coordination
  - the create dialog
  - row actions
  - DOM hit-testing (`target.closest("td")`)
  - *(new)* the gear menu and its two settings overlays (columns, action bar)
  - *(new)* syncing PrimeReact's column order with `visibleColumns` (`resetColumnOrder` in a layout effect)
  - *(new)* blocking drops on frozen headers (a capture-phase `display:contents` wrapper)
- `useRowActions` went the right way: the action-bar layout lives there, not in the panel. `VisibilityChooser` is a proper reusable component, with a pure, tested `moveItem`.

**Fix:**
- Move `form.ts` layout parsing to `fields/layout.ts`.
- Pass "creatable projects" through `config.list` (or a hook injected by config).
- Split `EntityListPanel` into `useEntityList` (data), `useColumnSelection`, and a presentational `<EntityTable>`.

### 3.4 Type safety stops at the network boundary
- All API types are **hand-written** (`entities/*/types.ts`, `fields/types.ts`). The backend already has springdoc, yet nothing generates TS from `/v3/api-docs`. Every DTO change on the Java side is a silent runtime break. The API change report (`docs/api-changes-vs-main.md`) lists over 40 such shape changes on this branch alone.
- The registry is `Map<string, EntityTypeConfig<any, any>>` (`entities/registry.ts`), so `getEntityType(key)` returns `any`-typed rows to the panels. Entity keys are free strings (`"recordingUnit"`, `"project"`…) scattered across modules. The latest commits added more of them: `REFERENCE_TARGETS.entityType` in `fields/optionSources.ts`, `ValidationStatusButton`/`ValidationStatusCell`'s `entityType` prop, and the catalogue segments (`"find-types"`…) in `typeCatalog.ts`. A typo in any of them fails silently: the chip just isn't a link, or the cache update misses.
- Responses are cast (`JSON.parse(text) as T`, `api/client.ts`) with no runtime validation.

**Fix:**
- Generate types with `openapi-typescript` in the Maven build, and fail CI on drift.
- Make the registry keyed by a string-literal union.
- Optionally validate at the adapter boundary with zod, just for detail responses.

### 3.5 Server-state hygiene
- **Query keys are ad-hoc string arrays** in ~40 places across 16 prefixes (`["entity-detail", …]`, `["recording-unit-effective-form", …]`, `["phase-effective-form", …]`), with no key factory.
- *(Progress.)* A status change (`ValidationStatusButton`) now writes the new value into the cached list rows and fiche with `setQueriesData`, instead of refetching every list on screen. That's the right pattern. But the component now has to know the list cache's shape (`PagedResult`) and the detail key's layout (`[prefix, entityType, id]`, matched by a hand-written `predicate`). That's exactly the knowledge a key factory should own.
- Invalidation is still very broad in 9 places: `invalidateQueries({ queryKey: ["entity-detail"] })` and `["entity-list"]` wipe *every* entity type after a single action (`PanelToolbar` bookmark, `useRowActions` bookmark/duplicate, `EntityDetailPanel`, creation from a relation tab). Broad invalidation now costs more than before, because each loaded chunk may carry column supplements (§3.12).
- The `QueryClient` is still created with defaults (`App.tsx:15`): 0 ms `staleTime`, 3 retries, refetch on focus. That's aggressive for form catalogues that effectively never change during a session.

**Fix:**
- Add a `queryKeys` module per entity.
- Invalidate `[entity, id]` precisely, or use `setQueryData` from the PATCH response, which already returns the updated resource.
- Set `staleTime: Infinity` for type and form catalogues, and `retry: (n, e) => e.status >= 500 && n < 2`.

### 3.6 CSS isolation: the bundle restyles the host page
Before this branch's theming pass, `mount.ts` imported the Lara theme, `primereact.min.css`, PrimeIcons and **PrimeFlex**. It now imports `src/styles/bundle.ts`: PrimeIcons and `main-panel.css`, with Lara and PrimeFlex gone. The result is loaded into the JSF page, *after* the PrimeFaces theme and Bootstrap (`focus.xhtml:362`). Consequences:
- *(Resolved on this branch.)* PrimeFlex's global utilities (`.col-6`, `.grid`, `.flex`, `.hidden`, `.p-2`, …) collided with Bootstrap's same-named classes across the *whole* page. The fiche grid, the only part used, is now `main-panel.css`'s own `sia-grid`/`sia-col-N`, and PrimeFlex is no longer a dependency (bundle CSS 373 KB → 27 KB).
- *(Resolved on this branch.)* Lara's `:root` variables and `.p-*` rules used to fight the Siamois theme. The host theme now skins PrimeReact itself, through `theme-base/_primereact.scss`.
- `main-panel.css` has grown from 935 to **1,209 lines** of global selectors (new: `visibility-chooser-*`, gear menu, toolbar separators, compact status button, filler column). Most are scoped by a component-ish prefix (`entity-list-panel-*`, `visibility-chooser-*`), but they share one namespace with the JSF app.
- There are 62 inline `style={{…}}` objects (66 before). Some are documented workarounds (`App.tsx` forces Splitter `display:flex` because of `@layer primereact` ordering), but most are just layout.

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

*(Update after `e31e1de8d`.)* The JSF side of the main panel is gone: the Java panels are now only descriptors (`AbstractPanel`, `AbstractListPanel`, `AbstractEntityPanel`), and `template.js`, jQuery UI, the JSF tables and the lazy data models were deleted (around 45k lines). The coupling surface is therefore smaller and easier to see: `MountOptions` plus the three bridge commands. It's a good moment to write down the typed contract recommended below, while the JSF side is small enough to check by hand.

**Fix:**
- Write the bridge contract down as a typed, versioned interface, and add one test that checks the XHTML-generated options object against it.
- Treat the bridge as an event bus (`CustomEvent`s on the mount element) rather than callbacks, so either side can be replaced independently.

### 3.8 Error handling and UX resilience
- There's **no React error boundary**. A render exception in one fiche field blanks the whole panel, including the other pane. The latest commits show the risk is real: an open `OverlayPanel` threw on the first scroll because `PrimeReactProvider` was missing. It's fixed in `mount.ts`, but with a boundary the failure would have stayed inside one pane.
- Error messages are per-component strings (`"Échec du favori"`, `"Impossible de charger…"`) rendered in `<Message>`. There is no shared toast or notification channel and no mapping of `ApiError.status` to user messages. A `403` and a `500` look the same.
- `ApiError` keeps only `status` and `message`. The backend's `ResponseStatusException` body (reason, path) is partly lost.

**Fix:**
- Add one `<ErrorBoundary>` per pane, plus one per fiche panel.
- Use a `useNotify()` backed by a PrimeReact `Toast` at App level.
- Add a status → message map.

### 3.9 i18n, accessibility, conventions
- **All UI strings are hard-coded French.** The JSF app has `langBean` and resource bundles; the React bundle has no i18n layer, so a language switch in JSF won't translate the React panel. Even a trivial `t()` over a JSON catalogue generated from the existing `.properties` files would do. Every new component makes this more expensive (the gear menu, `VisibilityChooser` and the compact toolbar menu added about 20 more strings).
- Accessibility is mostly left to PrimeReact, but it's improving: 42 hand-written `aria-*` attributes (29 before). Prev/next are now real `Button`s with `aria-label`, not `<a role="button">`, and `VisibilityChooser` has a keyboard path for reordering (Alt+↑/↓ on the handle). Still to do: clickable table cells and `CellEditOverlay` need keyboard paths and focus return. Worth an axe pass.
- **Comment density is very high:** ~2,350 comment lines for ~10.8k lines of code. Many cite "plan §x phase y", or explain *history* ("the bug this fixes") rather than intent. That's useful during the migration, but it will rot. Move the rationale to ADRs and PR descriptions, and keep code comments to the non-obvious *why*.
- **Tooling gaps:**
  - `npm run lint` still fails: ESLint 9 is installed, but there's no `eslint.config.js`. There's no Prettier config either. The missing `react-hooks` rules now show in new code: `useMemo(..., [fields.join(",")])` in `useVirtualList.ts`, and a `useLayoutEffect` keyed on a joined string in `EntityListPanel`. Both are intentional, but nothing checks the ones that aren't.
  - ~~Two unhandled errors leak out of `CreateForm.test.tsx`~~ (fixed by mocking `searchCreatableProjects`).
  - The test run prints about 3,900 `Could not parse CSS stylesheet` jsdom errors (the styles PrimeReact injects at runtime) plus `ReactDOMTestUtils.act` deprecation warnings. Everything passes, but a real warning won't be noticed in that noise. Silence it in the Vitest setup (a jsdom `virtualConsole` filter, or `css: false`).

### 3.10 Scalability outlook
| Axis | Today | At 12+ entity types / 2+ teams |
|---|---|---|
| Panels | Generic, good | Holds |
| Entity modules | ~1k lines each, ~80 % copied | Linear growth of duplicate code, divergent bugs |
| Types | Hand-written | Drift with every backend change |
| Navigation | One component, reload on Back | Harder to add routes (settings, search pages) |
| Bundle | Single chunk, everything eager | Grows with every entity; no code-splitting per entity type (`registerEntityType` could take a lazy `import()`) |
| Styling | Host theme (Lara gone), but 1.2k lines of global CSS | Collisions multiply as more JSF screens sit next to React ones |

### 3.11 Evidence from the theming pass (2026-09-25)

These are concrete defects found while aligning the React panel with the JSF theme. They show where the current structure makes bugs easy to write.

- **Colour scheme tied to what JSF mounted.** The per-entity colour classes (`siamois-panel action-unit-panel …`) lived only on the JSF wrapper around the mount. After a client-side navigation to another entity type, and in the overview pane, React kept rendering under the *mounted* entity's colours. Fixed by `EntityTypeConfig.panelClass` plus `App.tsx paneClassName`. It's the same root cause as §3.7: state duplicated between JSF and React with no single owner.
- **`@layer primereact` vs Bootstrap.** PrimeReact 10 injects its structural CSS inside a cascade layer, and `primereact.min.css` is an empty stub. Bootstrap's unlayered reboot therefore overrides PrimeReact's own resets (`ul` padding in menus, for example). Nothing in the bundle documented or tested this. It's now handled in the shared theme.
- **Font never loaded.** Sass interpolated the JSF expression in `_fonts.scss`, so production `theme.css` pointed at `url("resource [fonts:…]")`. It's a build-level bug, but it shows there's no visual check of the compiled theme. The new harness pages (`frontend/dev/theme`, `frontend/dev/panels`) are a first step. A Playwright screenshot test over them would make this permanent.
- **A default that the API rejects.** `/recording-units/{id}/mobiliers` defaults `sort=creationTime:desc`, but the code path used as soon as a field filter is present rejects `creationTime` with a 400. That's the typing gap of §3.4 on the backend side: a shared, generated contract for sortable/filterable fields per list would make this impossible.
- **Dead styling hooks.** 46 of the 112 custom class names the JSX emits have no rule anywhere. Only 16 are shared with the JSF markup, although the theme's app-level rules are all keyed on JSF class names. Reusing the JSF names, which is now documented in `docs/theme-class-map.md`, is what lets one theme serve both apps.

### 3.12 What changed after the first review (`cce4d8e4c` → `e31e1de8d`)

The four commits since the first version were about the table and its actions, styling, and removing dead JSF code. In short: **the product got better and the generic layers held up, but `EntityListPanel` took most of the new weight, and the list now depends more on PrimeReact internals.**

**Good moves**
- **Validation status became generic.** `ColumnDef.leading` is gone. `EntityListPanel` renders `ValidationStatusCell` in every identifier cell, because `validated` is a root property of every entity. The cell is an editable picker when write mode and the row's rights allow it, and a read-only badge otherwise (never a disabled button). Status changes update the cache in place (§3.5).
- **Toolbar.** `PanelToolbar` now works from a list of actions (navigation group, then create/duplicate, then settings) instead of repeated `Button` blocks. In the narrow overview pane, the secondary actions fold into a "…" menu. Prev/next moved into the navigation group, and `SiblingNav` dropped its ref-targeted `Tooltip` workaround.
- **Reference chips open their fiche** through `useOpenEntity()` (`panels/entityNavigation.tsx`), with no prop threading.
- **Dynamic columns for finds**, and organization-level catalogues for recording units, phases, containers and finds.
- **The list waits for its columns.** A list with a schema holds its first request until the columns are seeded (`columnsSeeded`), instead of fetching once without them and again with them.
- **Browser-local list preferences** (`panels/listPreferences.ts`): visible columns, their order, and the action-bar layout. Reads are tolerant (versioned, validated, `try/catch`), and `reconcileActionBar` handles actions that were added or removed since the layout was saved. Well tested.

**New concerns**
1. **Column supplements multiply requests.** When a column is shown, `useVirtualList` fetches it once *per loaded chunk* (`fields=<that field>`). Supplements accumulate: each chunk's base fields are frozen when it's first requested. With K chunks loaded and N columns shown afterwards, that's K×N extra queries, and all of them sit under the `["entity-list", type]` prefix. A single cell edit then refetches K bases plus K×N supplements. Fixes:
   - one supplement per chunk carrying *all* its missing fields;
   - or, on refetch, re-base the chunk with the full current field set and drop its supplements.
2. **`cellMemo={false}` next to the search input's state.** `searchInput` lives in `EntityListPanel` (it's debounced before `setSearch`), so every keystroke re-renders the whole panel. With cell memoisation off, that includes every mounted cell and its field renderer. The virtual scroller limits this to one screen, but that screen can be 30 rows × 15 columns. Move the search box into its own component that only reports the debounced value.
3. **More dependence on PrimeReact internals**:
   - `resetColumnOrder()` in a layout effect, because DataTable keeps its own column order after a drag;
   - a capture-phase drop blocker, because DataTable only checks the *dragged* column's `reorderable`;
   - a `Button` ref cast, because the typings disagree with the runtime;
   - a `PrimeReactProvider` that has to be present or `OverlayPanel` throws.

   Each one is commented, which is good. None is covered by a test that would fail on a PrimeReact upgrade. Pin PrimeReact's minor version, and add one integration test per workaround (drag a header onto a frozen one; show a column after a drag).
4. **Two persistence mechanisms for table layout.** `listPreferences` (localStorage) is new, while `UiViewService`/`TableViewState` (server-side saved views) are still in the backend with no consumer (`react-migration-apres-merge.md` §5). Also, the localStorage key is `siamois.list.<type>.<context>`, with no organisation or user in it. Additional field ids are institution-specific, so switching organisation in the same browser silently drops the saved columns (`knownFieldIds` filters them out), and two people sharing a browser share one layout. Put `organizationId` in the key now, and decide whether the server-side views are coming back or should be deleted.
5. **Free-string entity keys keep spreading** (§3.4).
6. **CSS and component size keep growing** (§3.3, §3.6). The table-settings UI (the gear `Menu`, two `OverlayPanel`s, their `VisibilityChooser`s, and the action-bar reconciliation) is about 100 lines inside `EntityListPanel` that could be an `<EntityTableSettings>` component taking `{catalog, visibleColumns, rowActions}`.

---

## 4. Prioritised recommendations

| # | Action | Value | Effort |
|---|---|---|---|
| 1 | Generate API types from OpenAPI (`openapi-typescript`) and wire into the build | High: kills a whole class of silent breaks | S |
| 2 | Extract `SchemaFicheTab` / `SchemaCreateForm` / `createEntityApi`, and delete the per-entity copies | High: roughly −3k lines, one place to fix bugs | M |
| 3 | ~~Drop Lara, host-theme parity~~ (**done on this branch**, see `docs/theme-class-map.md`); PrimeFlex dropped too; still to do: add a screenshot test over the dev harnesses | High: visual consistency, no page-wide collisions | S–M |
| 4 | Query-key factory, precise invalidation (started: status changes use `setQueriesData`), `staleTime` for catalogues | Medium: fewer refetch storms, snappier UI; more urgent now that chunks carry supplements | S |
| 4b | *(new)* One supplement per chunk for all missing fields, or re-base on refetch (§3.12 #1) | Medium: bounds request count after column toggles | S |
| 4c | *(new)* Isolate the search input from `EntityListPanel` render (§3.12 #2) | Medium: typing cost with `cellMemo={false}` | S |
| 5 | Error boundaries per pane plus a central notifier | Medium | S |
| 6 | Move generic code out of `entities/project` (`form.ts` → `fields/layout.ts`); remove the `panels → entities/project` import | Medium: dependency direction | S |
| 7 | Split `App.tsx` into a navigation reducer with URL decode (Back without reload); React now owns `popstate` alone | Medium | M |
| 8 | Split `EntityListPanel` (now 871 lines) into hooks, `<EntityTableSettings>` and a presentational table | Medium, rising | M |
| 9 | Add `eslint.config.js` (typescript-eslint, react-hooks, jsx-a11y) and Prettier; silence jsdom CSS noise in tests | Medium: currently no lint at all | S |
| 9b | *(new)* Add `organizationId` to the `listPreferences` key; decide the fate of server-side saved views | Low–Medium | S |
| 9c | *(new)* Pin the PrimeReact minor version, and add one integration test per DataTable workaround | Medium: upgrade safety | S |
| 10 | i18n layer seeded from the JSF bundles | Medium, higher if non-French users exist | M |
| 11 | Lazy-load entity modules | Low now, grows later | S |
| 12 | Trim migration-history comments into ADRs | Low | S |

**Bottom line:** the panel-level architecture is sound and above average for a JSF strangler migration. The debt sits one level down:
- per-entity copy-paste,
- untyped API contracts,
- global CSS,
- a monolithic `App`.

All four are cheap to fix *now*, with six entity types, and expensive at twelve.

The latest round confirms this. The new features (status in lists, table settings, column supplements) went through the generic layers without per-entity work, which is the architecture paying off. But nearly all of that weight landed in `EntityListPanel` and `main-panel.css`, and none of the four debts above got smaller.
