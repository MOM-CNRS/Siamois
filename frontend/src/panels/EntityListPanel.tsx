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
import type { ColumnDef, FilterValue, ListParams } from "../entities/types";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import { CellEditOverlay, type CellEditTarget } from "../components/table/CellEditOverlay";
import { ColumnFilter } from "../components/table/ColumnFilter";
import { ColumnToggler, type ColumnTogglerOption } from "../components/table/ColumnToggler";
import { renderAnswerValue } from "../fields/display";
import { filterKindForAnswerType, optionSourceFor, type FilterKind } from "../fields/optionSources";
import { resolveValueBinding, type FieldResource } from "../fields/types";
import type { PanelToolbarSlot } from "../mountOptions";
import { DEFAULT_LIMIT } from "./tableState";
import { useTableState } from "./useTableState";

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
}

// pages/shared/table/entityDataTable.xhtml's own paginator: rows-per-page 10/25/50, default 10
// (EntityTableViewModel.defaultPageSize). The React list used to default to 20 — a silent parity
// divergence, corrected here.
const ROWS_PER_PAGE_OPTIONS = [10, 25, 50];

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
  const queryClient = useQueryClient();

  // The one shared edit surface for every editable cell (plan phase 4b) — a single OverlayPanel
  // instance, re-anchored and re-seeded per click, not one per cell/column.
  const [editTarget, setEditTarget] = useState<CellEditTarget<RowRecord> | null>(null);
  const editOverlayRef = useRef<OverlayPanel>(null);

  const hasSchema = config?.list.schema != null;
  const { data: catalog } = useQuery({
    queryKey: ["entity-list-schema", entityType, organizationId],
    queryFn: () => config!.list.schema!.load({ organizationId }),
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
  };

  const { data, isLoading, error } = useQuery({
    queryKey: ["entity-list", entityType, params],
    queryFn: () => config!.api.list(params),
    enabled: config != null,
  });

  const canPatchAnswers = config?.api.patchAnswers != null;

  function canEditRow(row: RowRecord): boolean {
    const permissions = (row as { _permissions?: { canEdit?: boolean } })._permissions;
    return permissions?.canEdit === true;
  }

  // Opens the shared overlay for one (row, field) — and, critically, stops the click from
  // bubbling to DataTable's onRowClick, which is how a click on an editable cell doesn't also
  // navigate away (the plan's "collision" between row-click-navigates and click-to-edit).
  function openCellEditor(e: SyntheticEvent, row: RowRecord, field: FieldResource) {
    e.stopPropagation();
    if (!canPatchAnswers || !canEditRow(row)) return;
    setEditTarget({ row, field });
    editOverlayRef.current?.show(e, e.currentTarget as HTMLElement);
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
          render: (row: RowRecord) => {
            const display = renderAnswerValue(field, binding.read(row));
            // Only rendered as a click target when the row's own _permissions.canEdit says so —
            // gated server-side (ProjectApiService.patchProject's own permission check is the
            // real enforcement; this just avoids offering a control that would 403).
            if (!canPatchAnswers || !canEditRow(row)) return display;
            return (
              <span
                className="entity-list-panel-editable-cell"
                onClick={(e) => openCellEditor(e, row, field)}
              >
                {display}
              </span>
            );
          },
        };
      })
      .filter((c): c is ColumnDef<RowRecord> => c != null);
  }, [catalog, state.visibleColumns, canPatchAnswers]);

  const togglerOptions = useMemo<ColumnTogglerOption[]>(() => {
    if (!catalog) return [];
    return catalog.columns
      .slice()
      .sort((a, b) => a.order - b.order)
      .map((c) => ({ fieldId: c.fieldId, label: catalog.fields[c.fieldId]?.label ?? c.fieldId }));
  }, [catalog]);

  // "Filtres activés"/"Filtres désactivés" (tableToolbar.xhtml's gear-overlay toggle) — the filter
  // row is opt-in, not shown by default, matching a collapsed-by-default reading of that toggle
  // (JSF's own default isn't independently observable from the migration source).
  const [filtersEnabled, setFiltersEnabled] = useState(false);

  // Filterable columns: pinned ones marked `filterable` (their own key doubles as the f.<key>
  // query param — see ColumnDef.filterable) plus visible catalog columns whose answerType maps to
  // a FilterKind. A column not currently visible gets no filter widget, same as it gets no cell:
  // requesting f.<key> for a column ProjectListFilter doesn't know about is a 400, and a column
  // that isn't shown has nothing for the user to correlate the filter with anyway.
  const filterSpecs = useMemo(() => {
    const pinned = (config?.list.columns ?? [])
      .filter((c): c is ColumnDef<RowRecord> & { filterable: true } => c.filterable === true)
      .map((c) => ({ key: c.key, label: c.header, kind: "contains" as FilterKind, loadOptions: undefined }));

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
        return [{ key: c.columnId, label: field.label, kind, loadOptions }];
      });
    return [...pinned, ...dynamic];
  }, [config, catalog, state.visibleColumns, organizationId]);

  function onFilterChange(key: string, value: FilterValue | undefined) {
    const next = { ...state.filters };
    if (value) next[key] = value;
    else delete next[key];
    setFilters(next);
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

  function onSelectionChange(e: DataTableSelectionMultipleChangeEvent<RowRecord[]>) {
    setSelectedRows(e.value);
  }

  const rows = (data?.data ?? []) as RowRecord[];
  const allColumns: ColumnDef<RowRecord>[] = [...(config.list.columns as ColumnDef<RowRecord>[]), ...dynamicColumns];

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
      {(config.list.searchable || onCreate || hasSchema || filterSpecs.length > 0) && (
        // p:toolbar (pages/shared/table/tableToolbar.xhtml) → PrimeReact Toolbar, not a plain
        // div — its own generated classes are what render the chrome the stock theme actually
        // paints, the legacy class name alone doesn't. Search + gear (column toggler, filter
        // enable/disable) on the left, create on the right, same as the real toolbar's two
        // toolbarGroups.
        <Toolbar
          className="entity-list-panel-toolbar"
          start={
            <>
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
              {(hasSchema || filterSpecs.length > 0) && (
                <>
                  <Button
                    icon="bi bi-gear"
                    text
                    rounded
                    aria-label="Colonnes"
                    className="entity-list-panel-gear-button"
                    onClick={(e) => columnTogglerRef.current?.toggle(e)}
                  />
                  <OverlayPanel ref={columnTogglerRef}>
                    {hasSchema && (
                      <ColumnToggler options={togglerOptions} value={state.visibleColumns} onChange={setVisibleColumns} />
                    )}
                    {filterSpecs.length > 0 && (
                      <div className="entity-list-panel-filter-toggle">
                        <label>
                          <input
                            type="checkbox"
                            checked={filtersEnabled}
                            onChange={(e) => setFiltersEnabled(e.target.checked)}
                          />
                          {filtersEnabled ? "Filtres activés" : "Filtres désactivés"}
                        </label>
                      </div>
                    )}
                  </OverlayPanel>
                </>
              )}
            </>
          }
          end={onCreate && <Button label="Créer" icon="bi bi-plus-square" onClick={onCreate} />}
        />
      )}
      {filtersEnabled && filterSpecs.length > 0 && (
        // Deliberately a standalone row above the table rather than PrimeReact's native
        // filterDisplay="row" per-column headers: filtering here is server-side and lazy (our own
        // ProjectListFilter contract), not PrimeReact's built-in client filter model, so its own
        // per-column filter UI doesn't fit without fighting it. One ColumnFilter per filterable,
        // currently-visible column, matching filterTemplate.xhtml's filter widget types.
        <div className="entity-list-panel-filters">
          {filterSpecs.map((spec) => (
            <ColumnFilter
              key={spec.key}
              label={spec.label}
              kind={spec.kind}
              value={state.filters[spec.key]}
              onChange={(value) => onFilterChange(spec.key, value)}
              loadOptions={spec.loadOptions}
            />
          ))}
        </div>
      )}
      {error && <div className="entity-list-panel-error">{(error as Error).message}</div>}
      <DataTable
        value={rows}
        lazy
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
      >
        {/* selectedCountChip (tableToolbar.xhtml): selected/total, in the selection column's own
            header, same position as the JSF table. handleSelectionChange() is a no-op in JSF —
            selection drives nothing there either; this is decoration until a bulk action exists. */}
        <Column
          selectionMode="multiple"
          headerStyle={{ width: "3rem" }}
          header={<Chip label={`${selectedRows.length}/${data?.totalCount ?? 0}`} />}
        />
        {allColumns.map((col) => (
          <Column
            key={col.key}
            field={col.key}
            header={col.header}
            sortable={col.sortable}
            // Only the identifier column is clickable (plan §8 phase 5) — matches JSF's own
            // CommandLinkColumn, the only cell in the real table that navigates/opens anything.
            body={
              col.identifier
                ? (row: RowRecord) => (
                    <span
                      className="entity-list-panel-identifier-link"
                      style={{ cursor: "pointer", color: "var(--main-color)" }}
                      onClick={(e) => openIdentifier(e, row)}
                    >
                      {col.render(row)}
                    </span>
                  )
                : col.render
            }
          />
        ))}
      </DataTable>
      {canPatchAnswers && (
        // The one shared edit surface (plan phase 4b) — re-anchored per click via
        // editOverlayRef.current.show(event, anchorEl) in openCellEditor, not one instance per cell.
        <OverlayPanel
          ref={editOverlayRef}
          onHide={() => setEditTarget(null)}
        >
          <CellEditOverlay
            target={editTarget}
            organizationId={organizationId}
            onSave={(id, answers) => config.api.patchAnswers!(id, answers)}
            onSaved={onCellEditSaved}
            onClose={() => editOverlayRef.current?.hide()}
          />
        </OverlayPanel>
      )}
    </Panel>
  );
}
