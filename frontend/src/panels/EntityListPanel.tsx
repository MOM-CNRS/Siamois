import { useEffect, useMemo, useRef, useState, type SyntheticEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { DataTable, type DataTableSelectionMultipleChangeEvent, type DataTableStateEvent } from "primereact/datatable";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import { Panel } from "primereact/panel";
import { Toolbar } from "primereact/toolbar";
import { Chip } from "primereact/chip";
import { Button } from "primereact/button";
import { OverlayPanel } from "primereact/overlaypanel";
import { getEntityType } from "../entities/registry";
import type { ColumnDef, FilterValue, ListParams, ListScope } from "../entities/types";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import { CellEditOverlay, type CellEditTarget } from "../components/table/CellEditOverlay";
import { FilterChipBar, type FilterSpec } from "../components/table/FilterChipBar";
import { ColumnToggler, type ColumnTogglerOption } from "../components/table/ColumnToggler";
import { renderAnswerCell, renderAnswerValue } from "../fields/display";
import { filterKindForAnswerType, optionSourceFor, type FilterKind, type FilterOption } from "../fields/optionSources";
import { resolveValueBinding, type FieldResource } from "../fields/types";
import type { PanelToolbarSlot } from "../mountOptions";
import { DEFAULT_LIMIT } from "./tableState";
import { useTableState } from "./useTableState";
import { useWriteMode } from "./writeMode";

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
}

// Deliberate divergence from JSF's own entityDataTable.xhtml paginator (rows-per-page 10/25/50,
// default 10 — EntityTableViewModel.defaultPageSize): the React list uses 20/50/100, default 20
// (DEFAULT_LIMIT in tableState.ts).
const ROWS_PER_PAGE_OPTIONS = [20, 50, 100];

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
}: EntityListPanelProps) {
  const config = getEntityType(entityType);
  const { state, setPage, setSort, setSearch, setVisibleColumns, setFilters, seedVisibleColumns } = useTableState({
    defaultSort: config?.list.defaultSort,
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
  const columnTogglerRef = useRef<OverlayPanel>(null);
  const createOverlayRef = useRef<OverlayPanel>(null);
  const queryClient = useQueryClient();

  // The one shared edit surface for every editable cell (plan phase 4b) — a single instance,
  // re-anchored (to the clicked cell's own rect) and re-seeded per click, not one per cell/column.
  const [editTarget, setEditTarget] = useState<CellEditTarget<RowRecord> | null>(null);
  const writeMode = useWriteMode();

  const hasSchema = config?.list.schema != null;
  const { data: catalog } = useQuery({
    queryKey: ["entity-list-schema", entityType, organizationId, scope?.entityType, scope?.id],
    queryFn: () => config!.list.schema!.load({ organizationId, scope }),
    enabled: hasSchema,
  });

  // Seeds the toggler from the catalog's own defaults (ActionUnitTableColumnDefaults on the
  // project side) the first time it loads — never again, so a user's toggle isn't clobbered by an
  // organizationId change re-fetching the same catalog.
  useEffect(() => {
    if (!catalog) return;
    const defaults = catalog.columns
      .filter((c) => c.visible)
      .slice()
      .sort((a, b) => a.order - b.order)
      .map((c) => c.fieldId);
    seedVisibleColumns(defaults);
  }, [catalog, seedVisibleColumns]);

  // Only the columns currently on screen are ever requested — the projection (and its label-batch
  // resolution cost server-side) scales with what's visible, not with the whole catalog.
  const fieldsParam = state.visibleColumns.length > 0 ? state.visibleColumns.join(",") : undefined;

  const params: ListParams = {
    offset: state.offset,
    limit: state.limit,
    search: state.search,
    sort: state.sort,
    organizationId,
    fields: fieldsParam,
    filters: Object.keys(state.filters).length > 0 ? state.filters : undefined,
    scope,
  };

  const { data, isLoading, error } = useQuery({
    queryKey: ["entity-list", entityType, params],
    queryFn: () => config!.api.list(params),
    enabled: config != null,
  });

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
    return state.visibleColumns
      .map((fieldId) => byFieldId.get(fieldId))
      .filter((c): c is NonNullable<typeof c> => c != null)
      .sort((a, b) => a.order - b.order)
      .map((c): ColumnDef<RowRecord> | null => {
        const field = catalog.fields[c.fieldId];
        if (!field) return null;
        const binding = resolveValueBinding(field);
        return {
          key: field.id,
          header: field.label,
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
      if (field) byKey.set(field.id, field);
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
        const kind = filterKindForAnswerType(field.answerType);
        if (!kind) return [];
        const loadOptions = organizationId != null ? (optionSourceFor(field, organizationId) ?? undefined) : undefined;
        return [{ key: c.columnId, label: field.label, kind, loadOptions } satisfies FilterSpec];
      });
    return [...pinned, ...dynamic];
  }, [config, catalog, state.visibleColumns, organizationId]);

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

  if (!config) {
    return <div className="entity-list-panel-unsupported">Unknown entity type &quot;{entityType}&quot;</div>;
  }

  function onPage(e: DataTableStateEvent) {
    setPage(e.first ?? 0, e.rows ?? DEFAULT_LIMIT);
  }

  function onSortChange(e: DataTableStateEvent) {
    setSort(e.sortField ? `${e.sortField}:${e.sortOrder === 1 ? "asc" : "desc"}` : undefined);
  }

  // Only the identifier cell is clickable (plan §8 phase 5), matching JSF's own CommandLinkColumn
  // — the rest of the row is inert, unlike the pre-phase-5 whole-row-click-navigates behavior.
  // Prefers opening the overview (JSF's own row-click target) over a full navigation; onNavigate
  // is the fallback for a caller with no overview pane at all.
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
          className="entity-list-panel-identifier-link entity-nav-chip"
          role="button"
          tabIndex={0}
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

  const rows = (data?.data ?? []) as RowRecord[];
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
      {(config.list.searchable || onCreate || config.list.createForm || hasSchema) && (
        // p:toolbar (pages/shared/table/tableToolbar.xhtml) → PrimeReact Toolbar, not a plain
        // div — its own generated classes are what render the chrome the stock theme actually
        // paints, the legacy class name alone doesn't. Gear (column show/hide only) then search on
        // the left, create on the right. The gear leads because it acts on the table's shape,
        // which the search box does not; filtering moved out of it entirely, into the chip bar in
        // the table header below.
        <Toolbar
          className="entity-list-panel-toolbar"
          start={
            <>
              {hasSchema && (
                <>
                  <Button
                    icon="bi bi-gear"
                    text
                    rounded
                    size="large"
                    aria-label="Colonnes"
                    tooltip="Afficher / masquer des colonnes"
                    className="entity-list-panel-gear-button"
                    onClick={(e) => columnTogglerRef.current?.toggle(e)}
                  />
                  <OverlayPanel ref={columnTogglerRef}>
                    <ColumnToggler options={togglerOptions} value={state.visibleColumns} onChange={setVisibleColumns} />
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
            </>
          }
          end={
            config.list.createForm && config.list.createRequiresScope && !scope ? (
              // JSF's ToolbarCreateConfig "unavailable" state: the button stays visible but disabled,
              // explaining where creation is possible instead.
              <Button
                label="Créer"
                icon="bi bi-plus-square"
                disabled
                tooltip={config.list.createRequiresScope}
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
                    onCreated: (id) => {
                      createOverlayRef.current?.hide();
                      queryClient.invalidateQueries({ queryKey: ["entity-list", entityType] });
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
      <DataTable
          value={rows}
        // pages/shared/table/entityDataTable.xhtml is size="small" too — the React table had been
        // left at the theme's default (1rem cell padding vs 0.5rem), which is a big part of why it
        // read as much airier than the JSF one. entity-list-panel's own CSS tightens the vertical
        // padding further on top of this.
        size="small"
        // The table's own header, not a strip above it: the filter chips belong to the table, and
        // this is where JSF puts its filter affordance too (entityDataTable.xhtml's header facet).
        // Rendered only when there is at least one filterable column.
        header={
          filterSpecs.length > 0 ? (
            <FilterChipBar
              specs={filterSpecs}
              filters={state.filters}
              optionsByKey={filterOptionsByKey}
              onChange={onFilterChange}
            />
          ) : undefined
        }
        lazy
        reorderableColumns
        paginator
        first={state.offset}
        rows={state.limit}
        rowsPerPageOptions={ROWS_PER_PAGE_OPTIONS}
        totalRecords={data?.totalCount ?? 0}
        loading={isLoading}
        onPage={onPage}
        onSort={onSortChange}
        sortMode="single"
        dataKey="id"
        // "overview-open" mirrors EntityTableViewModel.getRowStyleClass's own row highlight for
        // the entity currently shown in the overview pane; "search-match" mirrors the same
        // method's search-hit class — the flat list has no tree-ancestor case to exclude, so
        // every currently-visible row already matched the server-side search (plan §8 phase 5).
        rowClassName={(row: RowRecord) => {
          const classes: string[] = [];
          if (overviewEntityId != null && row.id === overviewEntityId) classes.push("overview-open");
          if (state.search) classes.push("search-match");
          return classes.join(" ");
        }}
        selectionMode="checkbox"
        selection={selectedRows}
        onSelectionChange={onSelectionChange}
        // Sticky header + frozen columns both require this; it also moves the scrolling from the
        // page onto the table's own body, which is the rule this shell is built on (the app is a
        // fixed 100vh frame — every panel scrolls inside itself, the document never does).
        // scrollHeight="flex" means "take the height my parent gives me"; the flex chain that
        // actually gives it one is in main-panel.css.
        scrollable
        scrollHeight="flex"
      >
        {/* selectedCountChip (tableToolbar.xhtml): selected/total, in the selection column's own
            header, same position as the JSF table. handleSelectionChange() is a no-op in JSF —
            selection drives nothing there either; this is decoration until a bulk action exists. */}
        <Column
          selectionMode="multiple"
          headerStyle={{ width: "3rem" }}
          frozen={lastFrozenIndex >= 0}
          header={<Chip label={`${selectedRows.length}/${data?.totalCount ?? 0}`} />}
        />
        {allColumns.map((col, index) => (
          <Column
            key={col.key}
            field={col.key}
            frozen={index <= lastFrozenIndex}
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
            body={(row: RowRecord) => (
              <span className="entity-list-panel-cell">
                {col.leading?.(row)}
                {renderCellValue(col, row)}
              </span>
            )}
          />
        ))}
      </DataTable>
      {canPatchAnswers && (
        // The one shared edit surface (plan phase 4b) — re-anchored per click to the clicked
        // cell's own rect (it renders on top of that cell), not one instance per cell.
        <CellEditOverlay
          target={editTarget}
          organizationId={organizationId}
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
              <Chip label={String(data?.totalCount ?? 0)} style={{ background: "transparent", color: "var(--main-color)" }} />
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
