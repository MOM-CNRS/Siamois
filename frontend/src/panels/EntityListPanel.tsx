import { useEffect, useLayoutEffect, useMemo, useRef, useState, type DragEvent, type RefObject, type SyntheticEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  DataTable,
  type DataTableSelectionMultipleChangeEvent,
  type DataTableStateEvent,
} from "primereact/datatable";
import type { VirtualScrollerLazyEvent } from "primereact/virtualscroller";
import { Skeleton } from "primereact/skeleton";
import { ProgressBar } from "primereact/progressbar";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import { Panel } from "primereact/panel";
import { Toolbar } from "primereact/toolbar";
import { Chip } from "primereact/chip";
import { Button } from "primereact/button";
import { OverlayPanel } from "primereact/overlaypanel";
import { Menu } from "primereact/menu";
import { getEntityType } from "../entities/registry";
import { scopeProjectId } from "../entities/scope";
import { searchCreatableProjects } from "../entities/project/api";
import type { ColumnDef, CreatePrefill, EntityTypeConfig, FilterValue, ListParams, ListScope, PagedResult } from "../entities/types";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import { CellEditOverlay, type CellEditTarget } from "../components/table/CellEditOverlay";
import { FilterChipBar, type FilterSpec } from "../components/table/FilterChipBar";
import { ColumnToggler, type ColumnTogglerOption } from "../components/table/ColumnToggler";
import { VisibilityChooser } from "../components/table/VisibilityChooser";
import { renderAnswerCell, renderAnswerValue } from "../fields/display";
import { filterKindForField, optionSourceFor, type FilterKind, type FilterOption } from "../fields/optionSources";
import { hasFieldRenderer } from "../fields/registry";
import { resolveValueBinding, type FieldResource } from "../fields/types";
import type { PanelToolbarSlot } from "../mountOptions";
import { useTableState } from "./useTableState";
import { isFieldPending, isPlaceholderRow, useVirtualList } from "./useVirtualList";
import { listPrefsKey } from "./listPreferences";
import { useWriteMode } from "./writeMode";
import { useRowActions } from "./useRowActions";
import { Message } from "primereact/message";

export interface EntityListPanelProps {
  entityType: string;
  organizationId?: number;
  // Full main-pane navigation — kept for callers with no overview pane at all (e.g. a future
  // "open full" affordance); the identifier cell itself now calls onOpenOverview instead (plan §8
  // phase 5), falling back to this when onOpenOverview isn't supplied.
  onNavigate?: (entityType: string, id: string | number) => void;
  // Opens the row's entity in the right-hand overview pane instead of replacing the list (plan §8
  // phase 5, JSF's own row-click behavior) — wired to the identifier cell only, matching JSF's
  // CommandLinkColumn (the rest of the row is inert, unlike the pre-phase-5 whole-row click).
  onOpenOverview?: (entityType: string, id: string | number) => void;
  // The overview's current entity id, when it's showing this same entity type — highlights that
  // row (plan §8 phase 5's rowClassName), mirroring EntityTableViewModel.getRowStyleClass's
  // "overview-open".
  overviewEntityId?: string | number;
  // Bridged from the list's own tableToolbar.xhtml create button (ActionUnitListPanel.
  // configureTableColumns's toolbarCreateConfig — a different gate than the main panel's own
  // creationUnitKind, and rendered inside the table's toolbar, not the panel titlebar). Omitted
  // entirely (no button) when JSF didn't wire one up either.
  onCreate?: () => void;
  // Omitted once the panel has navigated away from the entity this toolbar was built for
  // (App.tsx), or for a render with nothing to bridge to (tests).
  toolbar?: PanelToolbarSlot;
  // Scopes this list to one parent entity's own sub-collection (plan: generic related-list tab,
  // e.g. "the recording units of project 5") rather than the entity type's global list. Threaded
  // straight into ListParams.scope — see entities/listApi.ts for the URL it produces.
  scope?: ListScope;
  // Renders without the wrapping <Panel>/PanelHeaderBar (icon + plural label + count chip) — for
  // use inside a DetailTabDef's own TabPanel, which already supplies a title and (via
  // DetailTabDef.badge) a count. The toolbar (gear/search/create) and the DataTable are unchanged;
  // only the panel-level chrome around them is dropped.
  embedded?: boolean;
  // false: no "Créer" at all (a relation tab whose plain create wouldn't be linked to its parent).
  creatable?: boolean;
  // What the list's own "Créer" links the new entity to — a relation tab's parent (the recording
  // unit a new child or find belongs to, the place a new project is attached to).
  createPrefill?: CreatePrefill;
}

// Deliberate divergence from JSF's own entityDataTable.xhtml paginator: the React list has no
// pages, it virtual-scrolls over the whole result set, fetching aligned chunks as they come into
// view (useVirtualList). The virtual scroller positions rows by index * ROW_HEIGHT_PX, so every row
// must be exactly this tall — main-panel.css pins `.entity-list-panel .p-datatable-tbody > tr` to
// the same value (--entity-list-row-height); change both together. Cells are already one line
// each (see .entity-list-panel-cell), which is what makes a fixed height possible at all.
const ROW_HEIGHT_PX = 38;

// tableToolbar.xhtml's globalFilter is debounced 500ms server-side; 300ms here errs toward
// responsiveness since the request itself is already async. Either way: not one query per
// keystroke, which the previous version was.
const SEARCH_DEBOUNCE_MS = 300;

type RowRecord = Record<string, unknown> & { id?: string | number };

/**
 * Generic entity list — entirely driven by the registry (plan §3) plus, where supplied, an
 * entity's field catalog (plan §8 phase-list-2 follow-up: `config.list.schema`). No entity-specific
 * branching here: entities/<type>/config.tsx supplies pinned columns via `config.list.columns` and,
 * optionally, a dynamic column source via `config.list.schema` — an entity with no schema behaves
 * exactly as this component did before dynamic columns existed (pinned columns only, no toggler,
 * no `fields=` request param).
 */
export function EntityListPanel({
  entityType,
  organizationId,
  onNavigate,
  onOpenOverview,
  overviewEntityId,
  onCreate,
  toolbar,
  scope,
  embedded,
  creatable,
  createPrefill,
}: EntityListPanelProps) {
  const config = getEntityType(entityType);
  // Where this list's column and action-bar arrangement is remembered (listPreferences.ts).
  const prefsKey = listPrefsKey(entityType, scope);
  const { state, setSort, setSearch, setVisibleColumns, setFilters, seedVisibleColumns, columnsSeeded } = useTableState({
    defaultSort: config?.list.defaultSort,
    prefsKey,
  });

  // Decoupled from `state.search` so every keystroke re-renders the input immediately while the
  // query itself only fires SEARCH_DEBOUNCE_MS after typing stops — tableToolbar.xhtml's own
  // globalFilter does the same (filterDelay="300" on the underlying p:dataTable).
  const [searchInput, setSearchInput] = useState("");
  useEffect(() => {
    const handle = setTimeout(() => setSearch(searchInput), SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [searchInput, setSearch]);

  const [selectedRows, setSelectedRows] = useState<RowRecord[]>([]);
  const gearMenuRef = useRef<Menu>(null);
  const gearButtonRef = useRef<HTMLButtonElement | null>(null);
  const columnTogglerRef = useRef<OverlayPanel>(null);
  const actionBarSettingsRef = useRef<OverlayPanel>(null);
  const createOverlayRef = useRef<OverlayPanel>(null);
  const queryClient = useQueryClient();

  // The one shared edit surface for every editable cell (plan phase 4b) — a single instance,
  // re-anchored (to the clicked cell's own rect) and re-seeded per click, not one per cell/column.
  const [editTarget, setEditTarget] = useState<CellEditTarget<RowRecord> | null>(null);
  const writeMode = useWriteMode();

  // Created and duplicated entities open where a row's own identifier would (the overview).
  const rowActions = useRowActions({
    entityType,
    config: config as EntityTypeConfig<unknown, unknown> | undefined,
    organizationId,
    writeMode,
    onOpen: onOpenOverview ?? onNavigate,
    prefsKey,
  });

  // An organization-wide list of a kind created inside a project: its create form picks the project,
  // so "Créer" needs at least one project where the caller may create this kind.
  const createNeedsPickedProject = config?.list.createProjectKind != null && scopeProjectId(scope) == null;
  const creatableProjects = useQuery({
    queryKey: ["creatable-projects", config?.list.createProjectKind, organizationId],
    queryFn: () => searchCreatableProjects(organizationId!, config!.list.createProjectKind!, undefined, 1),
    enabled: createNeedsPickedProject && organizationId != null && creatable !== false,
  });
  const canCreateInSomeProject = (creatableProjects.data?.totalCount ?? 0) > 0;

  const hasSchema = config?.list.schema != null;
  const { data: catalog, isError: catalogFailed } = useQuery({
    queryKey: ["entity-list-schema", entityType, organizationId, scope?.entityType, scope?.id],
    queryFn: () => config!.list.schema!.load({ organizationId, scope }),
    enabled: hasSchema,
  });

  // Seeds the toggler the first time the catalog loads — from this browser's saved arrangement if
  // there is one, else from the catalog's own defaults (ActionUnitTableColumnDefaults on the
  // project side) — never again, so a user's toggle isn't clobbered by an organizationId change
  // re-fetching the same catalog.
  useEffect(() => {
    if (!catalog) return;
    const defaults = catalog.columns
      .filter((c) => c.visible)
      .slice()
      .sort((a, b) => a.order - b.order)
      .map((c) => c.fieldId);
    seedVisibleColumns(
      defaults,
      catalog.columns.map((c) => c.fieldId),
    );
  }, [catalog, seedVisibleColumns]);

  // Only the columns currently on screen are ever requested — the projection (and its label-batch
  // resolution cost server-side) scales with what's visible, not with the whole catalog. They're
  // passed to useVirtualList apart from the result-set params: showing a column fetches just that
  // column for the rows already loaded, hiding one fetches nothing.
  const params: Omit<ListParams, "offset" | "limit" | "fields"> = {
    search: state.search,
    sort: state.sort,
    organizationId,
    filters: Object.keys(state.filters).length > 0 ? state.filters : undefined,
    scope,
  };

  const {
    rows: virtualRows,
    totalCount,
    isLoading,
    isFetching,
    error,
    requestRange,
    resultSetKey,
  } = useVirtualList<RowRecord>({
    entityType,
    params,
    fields: state.visibleColumns,
    fetch: (p) => config!.api.list(p) as Promise<PagedResult<RowRecord>>,
    // A list with a schema waits for its columns, rather than fetching once without them and then
    // adding each one (a failed catalog just means no dynamic columns).
    enabled: config != null && (!hasSchema || columnsSeeded || catalogFailed),
  });

  // A new result set (search, sort, filter, columns) starts back at the top: the scroll position
  // belonged to the previous one, and would otherwise land the user somewhere arbitrary in the new.
  const tableRef = useRef<DataTable<RowRecord[]>>(null);
  useEffect(() => {
    tableRef.current?.getVirtualScroller()?.scrollToIndex(0);
  }, [resultSetKey]);

  const canPatchAnswers = config?.api.patchAnswers != null;

  // entityDataTable.xhtml gates an editable cell on isRendered(col, "writeMode", item), which is
  // the app's global read/write switch AND canUserEditRow(item) — the same two-part rule the
  // fiche and the panel header follow. Only a UI gate: ProjectApiService.patchProject is what
  // actually enforces it; this just avoids offering a control that would 403.
  function canEditRow(row: RowRecord): boolean {
    if (!writeMode) return false;
    const permissions = (row as { _permissions?: { canEdit?: boolean } })._permissions;
    return permissions?.canEdit === true;
  }

  // Opens the shared editor for one (row, field), anchored to the clicked cell's own rect so it
  // renders on top of that cell rather than as a popover below it — and, critically, stops the
  // click from bubbling to DataTable's onRowClick, which is how a click on an editable cell
  // doesn't also navigate away (the plan's "collision" between row-click-navigates and
  // click-to-edit).
  function openCellEditor(e: SyntheticEvent, row: RowRecord, field: FieldResource) {
    e.stopPropagation();
    if (!canPatchAnswers || !canEditRow(row)) return;
    // Anchored on the <td>, not on the clicked span: the span is only as wide as its text, so
    // anchoring to it would open an editor narrower than the cell it replaces (and offset from it
    // by the cell's padding).
    const target = e.currentTarget as HTMLElement;
    const cell = target.closest("td") ?? target;
    setEditTarget({ row, field, anchor: cell.getBoundingClientRect() });
  }

  const dynamicColumns = useMemo<ColumnDef<RowRecord>[]>(() => {
    if (!catalog) return [];
    const byFieldId = new Map(catalog.columns.map((c) => [c.fieldId, c]));
    // In the user's order (the chooser's "visible" list, or a header dragged in the table); the
    // catalog's own `order` only seeds the defaults.
    return state.visibleColumns
      .map((fieldId) => byFieldId.get(fieldId))
      .filter((c): c is NonNullable<typeof c> => c != null)
      .map((c): ColumnDef<RowRecord> | null => {
        const field = catalog.fields[c.fieldId];
        if (!field) return null;
        const binding = resolveValueBinding(field);
        return {
          key: field.id,
          header: field.label,
          // The server says which columns it can order (FieldResource.query): sort=<fieldId>:asc,
          // a multi-valued column by its number of values.
          sortable: field.query?.sortable === true,
          // Renders the VALUE only. Whether that value is also a click target is the panel's
          // business (see the Column body below), not the column's: the editable wrapper has to be
          // the cell's own value slot — it eats the cell's padding to make the whole cell
          // clickable — so it cannot be nested inside one.
          render: (row: RowRecord) => renderAnswerCell(field, binding.read(row)),
        };
      })
      .filter((c): c is ColumnDef<RowRecord> => c != null);
  }, [catalog, state.visibleColumns, canPatchAnswers]);

  // Which columns are editable, and with which field — keyed by the ColumnDef key the body
  // renderer below receives. Only catalog-driven columns can be: a pinned column has no
  // FieldResource to build an AnswerInput from.
  const editableFieldByColumn = useMemo<Map<string, FieldResource>>(() => {
    if (!catalog || !canPatchAnswers) return new Map();
    const byKey = new Map<string, FieldResource>();
    for (const fieldId of state.visibleColumns) {
      const field = catalog.fields[fieldId];
      // A field with no editor (action code, address) would open an empty overlay: it stays a
      // plain cell.
      if (field && hasFieldRenderer(field.answerType)) byKey.set(field.id, field);
    }
    return byKey;
  }, [catalog, state.visibleColumns, canPatchAnswers]);

  const togglerOptions = useMemo<ColumnTogglerOption[]>(() => {
    if (!catalog) return [];
    return catalog.columns
      .slice()
      .sort((a, b) => a.order - b.order)
      .map((c) => ({ fieldId: c.fieldId, label: catalog.fields[c.fieldId]?.label ?? c.fieldId }));
  }, [catalog]);

  // Labels for the ids an "in" filter carries — FilterValue itself is ids-only (the wire shape),
  // so the chip bar needs this to print "Statut: En cours" rather than "Statut: 4711".
  const [filterOptionsByKey, setFilterOptionsByKey] = useState<Record<string, FilterOption[]>>({});

  // Filterable columns: pinned ones marked `filterable` (their own key doubles as the f.<key>
  // query param — see ColumnDef.filterable) plus visible catalog columns whose answerType maps to
  // a FilterKind. A column not currently visible gets no filter widget, same as it gets no cell:
  // requesting f.<key> for a column ProjectListFilter doesn't know about is a 400, and a column
  // that isn't shown has nothing for the user to correlate the filter with anyway.
  const filterSpecs = useMemo<FilterSpec[]>(() => {
    const pinned = (config?.list.columns ?? [])
      .filter((c): c is ColumnDef<RowRecord> & { filterable: true } => c.filterable === true)
      .map((c): FilterSpec => ({ key: c.key, label: c.header, kind: "contains" as FilterKind }));

    if (!catalog) return pinned;
    const byFieldId = new Map(catalog.columns.map((c) => [c.fieldId, c]));
    const dynamic = state.visibleColumns
      .map((fieldId) => byFieldId.get(fieldId))
      .filter((c): c is NonNullable<typeof c> => c != null)
      .flatMap((c) => {
        const field = catalog.fields[c.fieldId];
        if (!field) return [];
        // f.<fieldId>, whatever the field (system or additional): FieldQueryService resolves it.
        const kind = filterKindForField(field);
        if (!kind) return [];
        const loadOptions =
          organizationId != null ? (optionSourceFor(field, organizationId, scopeProjectId(scope)) ?? undefined) : undefined;
        return [{ key: field.id, label: field.label, kind, loadOptions } satisfies FilterSpec];
      });
    return [...pinned, ...dynamic];
  }, [config, catalog, state.visibleColumns, organizationId, scope]);

  function onFilterChange(key: string, value: FilterValue | undefined, options?: FilterOption[]) {
    const next = { ...state.filters };
    if (value) next[key] = value;
    else delete next[key];
    setFilters(next);
    if (options) setFilterOptionsByKey((byKey) => ({ ...byKey, [key]: options }));
  }

  // Re-fetches the list (so the saved row's new value shows without a full page reload) and, in
  // case the same entity is open in a detail view or the overview pane, that too — the overlay
  // itself doesn't know which other views might be showing this row.
  function onCellEditSaved() {
    void queryClient.invalidateQueries({ queryKey: ["entity-list", entityType] });
    if (editTarget?.row.id != null) {
      void queryClient.invalidateQueries({ queryKey: ["entity-detail", entityType, editTarget.row.id] });
    }
  }

  // PrimeReact keeps its own column order once a header has been dragged, and from then on sorts
  // the children by it — a column shown later would land at the far end, and a reorder done in the
  // chooser would be ignored. Re-syncing it to the children after every change of columns keeps
  // `visibleColumns` the one source of truth for the order.
  const columnOrderSignature = [...(config?.list.columns ?? []).map((c) => c.key), ...state.visibleColumns].join(",");
  useLayoutEffect(() => {
    tableRef.current?.resetColumnOrder();
  }, [columnOrderSignature]);

  if (!config) {
    return <div className="entity-list-panel-unsupported">Unknown entity type &quot;{entityType}&quot;</div>;
  }

  function onLazyLoad(e: VirtualScrollerLazyEvent) {
    requestRange(Number(e.first), Number(e.last));
  }

  // With onSort set, PrimeReact treats sorting as controlled: it reads sortField/sortOrder back from
  // props to compute the next click (asc → desc) and to draw the header arrow. Without them it
  // always sees "no current sort", so every click re-emits the same asc sort and nothing changes.
  const sortSep = state.sort?.lastIndexOf(":") ?? -1;
  const sortField = state.sort ? (sortSep >= 0 ? state.sort.slice(0, sortSep) : state.sort) : undefined;
  const sortOrder = state.sort ? (sortSep >= 0 && state.sort.slice(sortSep + 1) === "desc" ? -1 : 1) : undefined;

  function onSortChange(e: DataTableStateEvent) {
    setSort(e.sortField ? `${e.sortField}:${e.sortOrder === 1 ? "asc" : "desc"}` : undefined);
  }

  // Only the identifier cell is clickable (plan §8 phase 5), matching JSF's own CommandLinkColumn
  // — the rest of the row is inert, unlike the pre-phase-5 whole-row-click-navigates behavior.
  // Opens the overview (JSF's own row-click target); onNavigate is the fallback for a caller with
  // no overview pane at all.
  function openIdentifier(e: SyntheticEvent, row: RowRecord) {
    e.stopPropagation();
    if (row.id == null) return;
    if (onOpenOverview) onOpenOverview(entityType, row.id);
    else onNavigate?.(entityType, row.id);
  }

  // Same target preference as openIdentifier, for a column linking to another entity.
  function openLinked(e: SyntheticEvent, targetType: string, id: string | number) {
    e.stopPropagation();
    if (onOpenOverview) onOpenOverview(targetType, id);
    else onNavigate?.(targetType, id);
  }

  // A header dragged in the table: the dynamic columns' new order becomes the visible-columns order
  // (and so what the chooser shows, and what's remembered). Pinned columns keep their place.
  function onColReorder(e: { columns: unknown[] }) {
    const dynamic = new Set(state.visibleColumns);
    const order = e.columns
      .map((col) => (col as { props?: { columnKey?: string } }).props?.columnKey)
      .filter((key): key is string => key != null && dynamic.has(key));
    setVisibleColumns(order);
  }

  function blockDropOnFrozenHeader(e: DragEvent) {
    if ((e.target as Element).closest?.("th.p-frozen-column")) e.stopPropagation();
  }

  function onSelectionChange(e: DataTableSelectionMultipleChangeEvent<RowRecord[]>) {
    setSelectedRows(e.value);
  }

  // The cell's one value element. An editable column renders its value inside the click-to-edit
  // box; everything else gets the plain value slot. The permission check is per ROW
  // (_permissions.canEdit), and it is only a UI gate — ProjectApiService.patchProject is what
  // actually enforces it; this just avoids offering a control that would 403.
  function renderCellValue(col: ColumnDef<RowRecord>, row: RowRecord) {
    if (col.identifier) {
      return (
        <span
          className="entity-list-panel-identifier-link entity-list-panel-row-identifier entity-nav-chip"
          role="button"
          tabIndex={0}
          title={onOpenOverview ? "Ouvrir dans l'aperçu" : undefined}
          onClick={(e) => openIdentifier(e, row)}
        >
          <i className={config!.icon} aria-hidden="true" />
          <span className="entity-nav-chip-label">{col.render(row)}</span>
        </span>
      );
    }

    if (col.link) {
      const target = col.link(row);
      if (!target) return <span className="entity-list-panel-cell-value" />;
      return (
        <span
          className="entity-list-panel-identifier-link entity-nav-chip"
          role="button"
          tabIndex={0}
          onClick={(e) => openLinked(e, target.entityType, target.id)}
        >
          <i className={getEntityType(target.entityType)?.icon ?? "bi bi-link"} aria-hidden="true" />
          <span className="entity-nav-chip-label">{col.render(row)}</span>
        </span>
      );
    }

    const field = editableFieldByColumn.get(col.key);
    if (!field || !canEditRow(row)) {
      return <span className="entity-list-panel-cell-value">{col.render(row)}</span>;
    }

    const content = col.render(row);
    return (
      <span
        className="entity-list-panel-editable-cell"
        role="button"
        tabIndex={0}
        // The cell is one line and may be truncated, so the full text is always reachable as the
        // native tooltip; an empty cell falls back to naming what clicking it would edit.
        title={renderAnswerValue(field, resolveValueBinding(field).read(row)) || `Modifier « ${field.label} »`}
        onClick={(e) => openCellEditor(e, row, field)}
      >
        {content || <span className="entity-list-panel-editable-cell-empty">—</span>}
      </span>
    );
  }

  // The gear: table settings, each in its own overlay anchored on the gear itself (the menu that
  // opened it is gone by then).
  const hasActionBarSettings = rowActions.items.length > 0;
  const hasGear = hasSchema || hasActionBarSettings;
  function openSettings(ref: RefObject<OverlayPanel>, e: { originalEvent: SyntheticEvent }) {
    const anchor = gearButtonRef.current;
    if (anchor) ref.current?.show(e.originalEvent, anchor);
    else ref.current?.toggle(e.originalEvent);
  }
  const gearMenuItems = [
    ...(hasSchema
      ? [{ label: "Colonnes…", icon: "bi bi-layout-three-columns", command: (e: { originalEvent: SyntheticEvent }) => openSettings(columnTogglerRef, e) }]
      : []),
    ...(hasActionBarSettings
      ? [{ label: "Barre d'actions…", icon: "bi bi-three-dots", command: (e: { originalEvent: SyntheticEvent }) => openSettings(actionBarSettingsRef, e) }]
      : []),
  ];

  // The action bar settings list every configurable action in the current layout's order; the
  // shown ones are the inline ones. Hidden ones keep their relative order (the "…" menu's).
  const itemsByKey = new Map(rowActions.items.map((item) => [item.key, item]));
  const actionBarOrder = rowActions.actionBar.order
    .map((key) => itemsByKey.get(key))
    .filter((item): item is NonNullable<typeof item> => item != null)
    .map((item) => ({ id: item.key, label: item.label, icon: item.icon }));
  function onActionBarChange(inline: string[]) {
    const rest = rowActions.actionBar.order.filter((k) => !inline.includes(k));
    rowActions.setActionBar({ order: [...inline, ...rest], inline });
  }

  const rows = virtualRows as RowRecord[];
  // A column that only makes sense across several parents (the row's project, on an
  // organization-wide list) is dropped inside a scoped relation tab, where it would repeat the
  // parent on every row.
  const pinnedColumns = (config.list.columns as ColumnDef<RowRecord>[]).filter((c) => !(scope && c.unscopedOnly));
  const allColumns: ColumnDef<RowRecord>[] = [...pinnedColumns, ...dynamicColumns];

  // Everything up to and including the identifier column stays put while the rest scrolls
  // sideways — the identifier is how you tell one row from another, so losing it is what makes a
  // wide table unreadable. Driven by ColumnDef.identifier, the flag that already marks that column
  // (the one whose cell navigates), so no entity type has to restate which column this is.
  // -1 (no entity declares an identifier column) freezes nothing, including the selection box.
  const lastFrozenIndex = allColumns.findIndex((col) => col.identifier);

  // The toolbar + table + edit overlay — identical whether or not the surrounding <Panel> chrome
  // is rendered (see `embedded` below). Only the wrapper differs: a standalone list gets its own
  // <Panel> header (icon/plural label/count chip), an embedded one (inside a DetailTabDef's own
  // TabPanel) gets neither, since the tab already supplies a title and a count via
  // DetailTabDef.badge.
  const body = (
    <>
      {(config.list.searchable || onCreate || config.list.createForm || hasGear || filterSpecs.length > 0) && (
        // p:toolbar (pages/shared/table/tableToolbar.xhtml) → PrimeReact Toolbar, not a plain
        // div — its own generated classes are what render the chrome the stock theme actually
        // paints, the legacy class name alone doesn't. Gear (table settings) then search then the
        // filter chips on the left, create on the right. The gear leads because it acts on the
        // table's shape, which the search box and filters do not.
        <Toolbar
          className="entity-list-panel-toolbar"
          start={
            <>
              {hasGear && (
                <>
                  <Button
                    // PrimeReact's Button forwards its ref to the <button> itself (its typings say
                    // the component instance).
                    ref={(button) => {
                      gearButtonRef.current = button as unknown as HTMLButtonElement | null;
                    }}
                    icon="bi bi-gear"
                    text
                    rounded
                    size="large"
                    aria-label="Paramètres du tableau"
                    aria-haspopup
                    tooltip="Paramètres du tableau"
                    className="entity-list-panel-gear-button"
                    onClick={(e) => gearMenuRef.current?.toggle(e)}
                  />
                  <Menu ref={gearMenuRef} popup model={gearMenuItems} className="entity-list-panel-gear-menu" />
                  <OverlayPanel ref={columnTogglerRef} className="entity-list-panel-settings-overlay">
                    <ColumnToggler options={togglerOptions} value={state.visibleColumns} onChange={setVisibleColumns} />
                  </OverlayPanel>
                  <OverlayPanel ref={actionBarSettingsRef} className="entity-list-panel-settings-overlay">
                    <VisibilityChooser
                      className="entity-list-panel-action-bar-settings"
                      items={actionBarOrder}
                      visible={rowActions.actionBar.inline.filter((k) => rowActions.actionBar.order.includes(k))}
                      onChange={onActionBarChange}
                      visibleTitle="Dans la ligne"
                      hiddenTitle="Dans le menu …"
                      locked={[{ id: "bookmark", label: "Favori", icon: "bi bi-bookmark" }]}
                    />
                  </OverlayPanel>
                </>
              )}
              {config.list.searchable && (
                <span className="p-input-icon-left">
                  <i className="bi bi-search" />
                  <InputText
                    placeholder={`Search ${config.labels.plural}`}
                    value={searchInput}
                    onChange={(e) => setSearchInput(e.target.value)}
                  />
                </span>
              )}
              {/* The filters narrow the same result set as the search box, so they sit right after
                  it, in the toolbar — not in a strip of their own above the columns. Rendered
                  only when there is at least one filterable column. */}
              {filterSpecs.length > 0 && (
                <FilterChipBar
                  specs={filterSpecs}
                  filters={state.filters}
                  optionsByKey={filterOptionsByKey}
                  onChange={onFilterChange}
                />
              )}
            </>
          }
          end={
            creatable === false ? null : config.list.createForm && createNeedsPickedProject && !canCreateInSomeProject ? (
              // No project to create in: the button stays visible but disabled (JSF's
              // ToolbarCreateConfig "unavailable" state), saying why.
              <Button
                label="Créer"
                icon="bi bi-plus-square"
                disabled
                tooltip={
                  creatableProjects.isLoading
                    ? undefined
                    : `Vous n'avez le droit de créer ce type (${config.list.createProjectKind === "find" ? "mobilier" : config.labels.singular.toLowerCase()}) dans aucun projet.`
                }
                tooltipOptions={{ showOnDisabled: true, position: "left" }}
              />
            ) : config.list.createForm ? (
              <>
                <Button
                  label="Créer"
                  icon="bi bi-plus-square"
                  onClick={(e) => createOverlayRef.current?.toggle(e)}
                />
                <OverlayPanel ref={createOverlayRef} className="entity-list-panel-create-overlay">
                  {config.list.createForm({
                    organizationId,
                    scope,
                    prefill: createPrefill,
                    onCreated: (id) => {
                      createOverlayRef.current?.hide();
                      queryClient.invalidateQueries({ queryKey: ["entity-list", entityType] });
                      // A linked create changes the parent's relation counts (its tab badges).
                      if (createPrefill) void queryClient.invalidateQueries({ queryKey: ["entity-detail"] });
                      // Go straight to the new entity's own fiche, the way JSF's creation dialog
                      // does today — not just its overview — falling back to onOpenOverview only
                      // for a caller with no full-navigate of its own (an embedded relation tab).
                      (onNavigate ?? onOpenOverview)?.(entityType, id);
                    },
                    onCancel: () => createOverlayRef.current?.hide(),
                  })}
                </OverlayPanel>
              </>
            ) : (
              onCreate && <Button label="Créer" icon="bi bi-plus-square" onClick={onCreate} />
            )
          }
        />
      )}
      {error && <div className="entity-list-panel-error">{(error as Error).message}</div>}
      {rowActions.error && (
        <Message
          severity="error"
          className="entity-list-panel-action-error"
          content={
            <span style={{ display: "flex", alignItems: "center", gap: "0.5em", width: "100%" }}>
              <span style={{ flex: 1 }}>{rowActions.error}</span>
              <Button icon="bi bi-x" text rounded size="small" aria-label="Fermer" onClick={rowActions.clearError} />
            </span>
          }
        />
      )}
      {/* Rows arriving: the placeholder skeletons show WHERE, this shows THAT something is
          loading even when those rows are off screen or already filled in. Same convention as the
          JSF panels' own p:progressBar (panelContent.xhtml's panel-progressbar): a 3px track that
          is always there, so showing it never shifts the table, animated only while loading.
          Not during the very first load — the table's own loading overlay covers that. */}
      <ProgressBar
        mode="indeterminate"
        className={`panel-progressbar entity-list-panel-progressbar${isFetching && !isLoading ? " is-active" : ""}`}
        aria-hidden={!(isFetching && !isLoading)}
      />
      {/* PrimeReact only checks that the DRAGGED column is reorderable, never the drop target, so a
          column dropped on the frozen block would land inside it (between identifier and
          actions). Swallowing drag events over a frozen header, in the capture phase before the
          header's own handler, keeps the frozen block closed. display:contents: no layout box,
          the panel's flex chain runs straight through. */}
      <div
        style={{ display: "contents" }}
        onDragOverCapture={blockDropOnFrozenHeader}
        onDropCapture={blockDropOnFrozenHeader}
      >
      <DataTable
          value={rows}
        // pages/shared/table/entityDataTable.xhtml is size="small" too — the React table had been
        // left at the theme's default (1rem cell padding vs 0.5rem), which is a big part of why it
        // read as much airier than the JSF one. entity-list-panel's own CSS tightens the vertical
        // padding further on top of this.
        size="small"
        ref={tableRef}
        lazy
        reorderableColumns
        // `lazy` here only defers the loading to onLazyLoad; `value` is still the full-length
        // array (placeholders where a chunk hasn't arrived — see useVirtualList), which is what
        // keeps the scrollbar to scale. delay debounces a fast fling into one request per
        // settled range instead of one per chunk flown past.
        virtualScrollerOptions={{ lazy: true, onLazyLoad, itemSize: ROW_HEIGHT_PX, delay: 150 }}
        loading={isLoading}
        // PrimeReact memoizes each cell on its row data, so a cell would ignore anything else its
        // body depends on — the action bar layout, write mode, the columns being fetched. The
        // virtual scroller keeps only a screenful of rows mounted, so re-rendering them is cheap.
        cellMemo={false}
        onColReorder={onColReorder}
        onSort={onSortChange}
        sortField={sortField}
        sortOrder={sortOrder}
        sortMode="single"
        dataKey="id"
        // "overview-open" mirrors EntityTableViewModel.getRowStyleClass's own row highlight for
        // the entity currently shown in the overview pane; "search-match" mirrors the same
        // method's search-hit class — the flat list has no tree-ancestor case to exclude, so
        // every currently-visible row already matched the server-side search (plan §8 phase 5).
        rowClassName={(row: RowRecord) => {
          if (isPlaceholderRow(row)) return "entity-list-panel-placeholder-row";
          const classes: string[] = [];
          if (overviewEntityId != null && row.id === overviewEntityId) classes.push("overview-open");
          if (state.search) classes.push("search-match");
          return classes.join(" ");
        }}
        selectionMode="checkbox"
        selection={selectedRows}
        onSelectionChange={onSelectionChange}
        isDataSelectable={(e) => !isPlaceholderRow(e.data)}
        // Sticky header + frozen columns both require this; it also moves the scrolling from the
        // page onto the table's own body, which is the rule this shell is built on (the app is a
        // fixed 100vh frame — every panel scrolls inside itself, the document never does).
        // scrollHeight="flex" means "take the height my parent gives me"; the flex chain that
        // actually gives it one is in main-panel.css.
        scrollable
        scrollHeight="flex"
      >
        {/* selectedCountChip (tableToolbar.xhtml): selected/total, in the selection column's own
            header, after the select-all box (main-panel.css reverses PrimeReact's title-then-
            checkbox order). handleSelectionChange() is a no-op in JSF — selection drives nothing
            there either; this is decoration until a bulk action exists. */}
        <Column
          columnKey="__selection"
          selectionMode="multiple"
          headerStyle={{ width: "3rem" }}
          headerClassName="entity-list-panel-selection-header"
          frozen={lastFrozenIndex >= 0}
          reorderable={false}
          header={<Chip label={`${selectedRows.length}/${totalCount}`} />}
        />
        {allColumns.flatMap((col, index) => [
          <Column
            key={col.key}
            columnKey={col.key}
            field={col.key}
            frozen={index <= lastFrozenIndex}
            // Only scrolling dynamic columns move: dragging one into or out of the frozen block
            // would desync `frozen` from the column's position, and the order that's kept (and
            // shown in the chooser) is the dynamic columns' — a pinned one dragged elsewhere would
            // snap back.
            reorderable={index > lastFrozenIndex && state.visibleColumns.includes(col.key)}
            // Header text is always one line, truncated with an ellipsis past the CSS max-width
            // (.entity-list-panel-column-header); the full label stays available as the title
            // tooltip. A wrapping header makes every row in the table taller for one long label.
            header={
              <span className="entity-list-panel-column-header" title={col.header}>
                {col.header}
              </span>
            }
            sortable={col.sortable}
            // Only the identifier column is clickable (plan §8 phase 5) — matches JSF's own
            // CommandLinkColumn, the only cell in the real table that navigates/opens anything.
            // `leading` (the validation-status badge) renders in the same cell but outside the
            // clickable chip, exactly like JSF's merged statusIdActionsCol.
            // Every cell goes through the same wrapper, so no cell is ever more than one line:
            // .entity-list-panel-cell is the flex row (leading badge + value) and the value slot
            // is the part that truncates with an ellipsis. Exactly one element fills that slot —
            // the identifier chip, the editable box, or a plain value — never two nested, because
            // the editable box has to reach out to the cell's padding edge and a clipping wrapper
            // around it would cut that off.
            body={(row: RowRecord) =>
              // A column just shown, still being fetched for this row: a skeleton, not an empty cell.
              isPlaceholderRow(row) || isFieldPending(row, col.key) ? (
                <Skeleton height="1rem" width={col.identifier ? "6rem" : "70%"} />
              ) : (
              <span className="entity-list-panel-cell">
                {col.leading?.(row)}
                {renderCellValue(col, row)}
              </span>
              )
            }
          />,
          // The row's actions (bookmark, duplicate, new child…) sit right after the identifier,
          // frozen with it — JSF merges both into one statusIdActionsCol.
          ...(index === Math.max(lastFrozenIndex, 0)
            ? [
                <Column
                  key="__row-actions"
                  columnKey="__row-actions"
                  reorderable={false}
                  className="entity-list-panel-row-actions-cell"
                  headerClassName="entity-list-panel-row-actions-cell"
                  frozen={lastFrozenIndex >= 0}
                  header={<span className="entity-list-panel-column-header" />}
                  body={(row: RowRecord) => (isPlaceholderRow(row) ? null : rowActions.render(row))}
                />,
              ]
            : []),
        ])}
        {/* An empty last column that takes up whatever width the table has left over, so a table
            with few columns keeps them at their content width instead of stretching them across
            the panel. It shrinks to nothing once the columns overflow. */}
        <Column
          key="__filler"
          columnKey="__filler"
          reorderable={false}
          className="entity-list-panel-filler-cell"
          headerClassName="entity-list-panel-filler-cell"
          header={null}
          body={() => null}
        />
      </DataTable>
      </div>
      {rowActions.dialog}
      {canPatchAnswers && (
        // The one shared edit surface (plan phase 4b) — re-anchored per click to the clicked
        // cell's own rect (it renders on top of that cell), not one instance per cell.
        <CellEditOverlay
          target={editTarget}
          organizationId={organizationId}
          entityType={entityType}
          onSave={(id, answers) => config.api.patchAnswers!(id, answers)}
          onSaved={onCellEditSaved}
          onClose={() => setEditTarget(null)}
        />
      )}
    </>
  );

  if (embedded) {
    // No <Panel>/PanelHeaderBar — the flex chain that gives scrollHeight="flex" its bound
    // otherwise comes from that <Panel>; reproduce it directly (styles/main-panel.css has the
    // matching `.entity-list-panel.embedded` rule for the tab body wrapping this).
    return (
      <div className="entity-list-panel embedded" style={{ display: "flex", flexDirection: "column", minHeight: 0, flex: 1 }}>
        {body}
      </div>
    );
  }

  return (
    // panel/header/actionUnitListPanelHeader.xhtml: icon + title + a count chip, plus the
    // generic toolbar — one single PanelHeaderBar passed as this <Panel>'s `header` (plan §7/§8
    // follow-up: "toolbar is part of the header", not PrimeReact's separate `icons` slot),
    // matching the real markup's one sideview-titlebar div rather than a separate strip above
    // this component.
    <Panel
      className="entity-list-panel"
      header={
        <PanelHeaderBar
          title={
            <div className="entity-list-panel-header" style={{ display: "flex", alignItems: "center", gap: "0.5em" }}>
              <i className={config.icon} style={{ fontSize: "2rem", color: "var(--main-color)" }} />
              <span style={{ paddingRight: "0.5em" }}>{config.labels.plural}</span>
              {/* *ListPanelHeader.xhtml's count chip: `<entity>-count-chip` (themed-panel's chip rule),
                  with the same inline transparent background/main-color text. */}
              <Chip
                label={String(totalCount)}
                className={config.panelClass ? config.panelClass.replace(/-panel$/, "-count-chip") : undefined}
                style={{ background: "transparent", color: "var(--main-color)" }}
              />
            </div>
          }
          toolbar={toolbar}
        />
      }
    >
      {body}
    </Panel>
  );
}
