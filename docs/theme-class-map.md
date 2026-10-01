# Theme parity: PrimeFaces `.ui-*` ↔ PrimeReact `.p-*`

The React main panel no longer ships a theme of its own (it bundled PrimeReact's **Lara** until 2026-09-25). Both apps are painted by the one Siamois PrimeFaces theme:

- **`primefaces-themes/theme-base/_primereact.scss`**, imported last by `themes/primefaces-siamois-theme/theme.scss`. Its three sections:
  1. **Class aliases**: `.p-x { @extend .ui-x; }`. Every rule of the stylesheet that mentions `.ui-x` also emits a `.p-x` variant. That covers theme-base *and* the app overrides in `siamois/**`. The `.ui-*` output is untouched, since `@extend` only appends selectors.
  2. **States**: see the state table below.
  3. **Structure**: see the structural table below.
- **`themes/primefaces-siamois-theme/_variables.scss` `:root`**: PrimeReact's CSS custom properties are derived from the designer variables. These are `--primary-color`, `--text-color`, `--text-color-secondary`, `--font-family`, `--highlight-bg` and `--highlight-text-color`. The other ones were already there.
- **`settings.scss`** is compiled on its own (`settings.css`), so the aliases it needs are declared in that file too.
- **`frontend/src/styles/main-panel.css`** keeps only what has no JSF counterpart.

## Tooling

| Command | What it does |
|---|---|
| `frontend/dev/build-theme.sh [--watch]` | Compiles `theme.scss`/`settings.scss` with the global `sass` into `frontend/dev/.generated/` (gitignored), plus PrimeFaces' `components.css` and primeicons from the local m2 jar |
| `npm run dev`, then `/resources/react-main-panel/dev/theme/harness.html` | Kitchen sink: each PrimeReact component the panel uses, next to the PrimeFaces markup of its JSF counterpart, under the real host stylesheets. `?only=button,datatable` filters rows; `?lara=1` re-adds Lara for a before/after view. |
| `…/dev/panels/panels.html?kind=list\|detail\|home&type=project&id=1&overview=2` | The **real** React panels, mounted like `focus.xhtml` does, against an in-browser mock API (`dev/panels/mockApi.ts`) |
| `frontend/dev/check-jsf-theme-regression.sh [ref]` | Proves the JSF output didn't change. It compiles the theme at `ref` (default `HEAD`) and in the working tree, then checks that every old rule survives with identical declarations and a superset of its selectors. |

## Component class map

"alias" means the rule comes through `@extend`. "explicit" means a hand-written block in `_primereact.scss` using the same `$variables`.

| Component | PrimeFaces | PrimeReact | How |
|---|---|---|---|
| Widget font | `.ui-widget` | `.p-component` | alias |
| Disabled | `.ui-state-disabled` | `.p-disabled`, `:disabled` | alias + explicit |
| Selected / active | `.ui-state-highlight`, `.ui-state-active` | `.p-highlight` | alias. It carries the app overrides, e.g. the light-green selected row and the sorted header. |
| Focus (class) | `.ui-state-focus` | `.p-focus` | alias |
| Invalid | `.ui-state-error` | `.p-invalid` | alias |
| Hover | `.ui-state-hover` (class set by JS) | `:hover` | explicit, per component |
| Keyboard focus | `.ui-state-focus` | `:focus-visible` | explicit, per component |
| Button | `.ui-button` + `-outlined` / `-flat` / `-raised` / `.rounded-button` / `-secondary` … `-danger` / `-icon-only` | `.p-button` + `-outlined` / `-text` / `-raised` / `-rounded` / severities / `-icon-only` | alias for colours. Explicit for padding: PrimeFaces pads `.ui-button-text` and absolutely positions the icon, while PrimeReact pads the button itself. |
| Text input | `.ui-inputfield`, `.ui-inputtextarea` | `.p-inputtext`, `.p-inputtextarea` | alias + explicit hover/focus |
| Input icons | `.ui-input-icon-left` / `-right` | `.p-input-icon-left` / `-right` | alias |
| AutoComplete | `.ui-autocomplete`, `-multiple-container`, `-token`, `-input-token`, `-panel`, `-items`, `-item` | same with `p-` | alias + explicit layout (flex tokens, input grows) |
| MultiSelect | `.ui-selectcheckboxmenu`, `-panel`, `-header`, `-items`, `-item`, `-token` | `.p-multiselect`, … | alias + explicit (label padding, chip tokens) |
| Checkbox | `.ui-chkbox`, `.ui-chkbox-box` | `.p-checkbox`, `.p-checkbox-box` | alias + explicit. PrimeReact 10 overlays a transparent native `<input>` and puts `.p-highlight` on the **root**. |
| Calendar | `.ui-datepicker` | `.p-datepicker` | alias + explicit (header, cells, month/year pickers) |
| Panel | `.ui-panel`, `-titlebar`, `-title`, `-content`, `-footer`, `-titlebar-icon` | `.p-panel`, `-header`, `-title`, `-content`, `-footer`, `-header-icon` | alias + explicit. The content sits in a `.p-toggleable-content` wrapper. |
| Card | `.ui-card`, `-body`, `-title`, `-subtitle`, `-content`, `-footer` | `.p-card…` | alias |
| Toolbar | `.ui-toolbar` | `.p-toolbar` (+ `-group-start/center/end`) | alias + explicit flex |
| TabView | `.ui-tabs`, `-nav`, `-panels`, `-panel`; `li.ui-tabs-header(.ui-state-active) > a` | `.p-tabview`, `-nav`, `-panels`, `-panel`; `li(.p-highlight) > a.p-tabview-nav-link` | alias for containers, explicit for headers (the padding sits on the `<a>`, two extra wrappers). The ink bar is hidden, since PrimeFaces has none. |
| Splitter | `.ui-splitter`, `-gutter`, `-gutter-handle` | same with `p-` | alias |
| DataTable | `.ui-datatable`, `-header`, `-footer`, `-data`; `thead th`, `.ui-sortable-column(-icon)`, `.ui-column-title` | `.p-datatable`, …, `.p-datatable-tbody`; `.p-sortable-column(-icon)`, `.p-column-title` | alias + explicit row/header hover, border-box `th` |
| Paginator | `.ui-paginator` | `.p-paginator` | alias (container only) |
| Dialog | `.ui-dialog`, `-titlebar`, `-title`, `-content`, `-footer` | `.p-dialog`, `-header`, `-title`, `-content`, `-footer` | alias + explicit header layout, close icon, mask |
| OverlayPanel | `.ui-overlaypanel`, `-content` | same with `p-` | alias + explicit close button and arrow |
| Tooltip | `.ui-tooltip`, `-text`, `-arrow`, `-right/left/top/bottom` | same with `p-` | alias |
| Menu | `.ui-menu`, `-list`, `.ui-menuitem`, `-link`, `-text`, `-icon` | `.p-menu`, `-list`, `.p-menuitem`, … (+ `.p-menuitem-content` wrapper) | alias + explicit hover (on the content wrapper) |
| BreadCrumb | `.ui-breadcrumb` (separators via `::before`) | `.p-breadcrumb` (separator `<li>` + SVG) | alias + explicit |
| Message | `.ui-message(-severity)` | `.p-inline-message(-severity)` | explicit (different class names) |
| Chip | `.ui-chip`, `-text`, `-icon`, `-remove-icon` | same with `p-` | alias |
| ProgressBar / Skeleton / Badge / Tag | `.ui-progressbar(-value/-label)`, `.ui-skeleton`, `.ui-badge`, `.ui-tag` | same with `p-` | alias |

### States that are deliberately not extended

`:hover`, `:focus-visible` and other pseudo-classes are **never** `@extend`ed. Measured, extending them would add a variant to every `.ui-state-hover` rule of every component: the theme grows to 1 MB minified / 79 KB gzipped, compared with 600 KB / 54 KB now.

## Bootstrap vs `@layer primereact`

PrimeReact 10's `primereact.min.css` is an empty, deprecated stub. Each component injects its structural CSS at runtime inside `@layer primereact`. Layered rules lose to **any** unlayered rule, and the host page loads Bootstrap's reboot unlayered. So the list resets Bootstrap overrides (`ul, ol { padding-left: 2rem; margin-bottom: 1rem }`) are restated unlayered at the top of section 2/3.

## App class names: JSX ↔ XHTML

| Where | JSF (`.xhtml` / bean) | React before | React now |
|---|---|---|---|
| Main pane root | `#{panelClass} panel-docked focus` on the `focus.xhtml` wrapper, e.g. `siamois-panel action-unit-panel single-panel` | `panel-splitter-panel-l` only. It inherited the class of the entity **JSF mounted**, even after navigating to another type client-side. | `panel-splitter-panel-l siamois-panel <entity>-panel single-panel\|list-panel`, computed from the view shown now (`App.tsx` `paneClassName`) |
| Overview pane root | `sideview #{parentOrOverview.panelClass}` (`panelContent.xhtml`) | `panel-splitter-panel-r sideview`: it inherited the **main** entity's colours | `panel-splitter-panel-r sideview siamois-panel <entity>-panel single-panel` |
| Entity → panel class | `ActionUnitPanel` … `ContainerPanel` constructors | none | `EntityTypeConfig.panelClass`: project → `action-unit-panel`, recordingUnit → `recording-unit-panel`, find → `specimen-panel`, phase → `phase-panel`, container → `container-panel`, place → `spatial-unit-panel` |
| List header count | `<p:chip styleClass="<entity>-count-chip">` (`*ListPanelHeader.xhtml`) | `<Chip>` with inline style only | `<Chip className="<entity>-count-chip">` + same inline style |
| Fiche header identifier chip | `editableIdentifierChip`: `<entity>-chip-alt entity-nav-chip` + the panel icon, filled in the entity colour | `entity-nav-chip` only, restyled outlined in the context colour by `main-panel.css` | `<entity>-chip-alt entity-nav-chip` + `icon`. The `main-panel.css` outline is now limited to list-row links (`.entity-nav-chip:not(.p-chip)`) |
| Fiche header type chip | `field.styleClass` = `mr-2 <entity>-type-chip` (`ActionUnitForm`, `PhaseForm`, …), outlined in the entity colour | project correct; **every other entity used `recording-unit-chip-alt`** (a copy-paste, so all types rendered in RU red) | `mr-2 <entity>-type-chip` |
| Detail tab title | `tabTitle.xhtml`: bold `title (count)` | `label <Chip>count</Chip>` | bold `label (count)`, same markup |
| Already shared | `panel-bc`, `panel-footer`, `panel-history-colored-span`, `sia-form-panel`, `ellipsis-btn`, `header-toggle-container`, `field-value-group`, `sideview-titlebar`, `sideview-topbar-button`, `entity-nav-chip`, `action-unit-type-chip`, `rounded-button`, `panel-progressbar`, `sia-welcome-card sia-<entity>`, `<entity>-count-chip-alt` | unchanged | unchanged. These are now also styled through the aliases (e.g. `.ui-panel.sia-form-panel .ui-panel-titlebar` → `.p-panel.sia-form-panel .p-panel-header`). |

### JSF inconsistencies found (not changed)

- `actionUnitListPanelHeader.xhtml` and `spatialUnitListPanelHeader.xhtml` both use `spatial-unit-count-chip`. For projects that's harmless, since both map to the same context colour. React uses the entity's own `action-unit-count-chip`.
- `spatialUnitPanelHeader.xhtml` uses `action-unit-chip-alt` for a place's identifier chip (same colour again). React uses `spatial-unit-chip-alt`.

### Open question (left as is)

In JSF list tables (`entityDataTable.xhtml`), the identifier chip is **filled**: `background: #{chipColor}; color: white`, plus the entity icon. React's list rows use an **outlined** context-colour chip (`main-panel.css`, `.entity-nav-chip:not(.p-chip)`). That looked like a deliberate choice, so it's unchanged.

### React-only class names still without a JSF counterpart

These are hooks for `main-panel.css` or tests, and there's no JSF equivalent to align them with:
- `entity-list-panel-*` and `entity-detail-panel-*`
- `cell-edit-overlay`
- `sia-fiche-tab` and `sia-md/lg-col-N` (the container-query grid)
- `*-fiche-tab`, `*-detail-header`, `*-create-form`
- `validation-status-*`
- `home-panel-*`

46 of them have no rule anywhere and are pure hooks. They're harmless, but they could be dropped.
