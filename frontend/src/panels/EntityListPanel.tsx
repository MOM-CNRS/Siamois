import { useCallback, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Skeleton } from "primereact/skeleton";
import { ProgressBar } from "primereact/progressbar";
import { Panel } from "primereact/panel";
import { Chip } from "primereact/chip";
import { Button } from "primereact/button";
import { Message } from "primereact/message";
import { getEntityType } from "../entities/registry";
import { scopeProjectId } from "../entities/scope";
import { searchCreatableProjects } from "../entities/creatableProjects";
import type { CreatePrefill, EntityPreview, EntityTypeConfig, FilterValue, ListScope, RowActionDef } from "../entities/types";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import { FilterChipBar } from "../components/table/FilterChipBar";
import type { FilterOption } from "../fields/optionSources";
import type { PanelToolbarSlot } from "../mountOptions";
import { queryKeys } from "../api/queryKeys";
import { EntityTable } from "./list/EntityTable";
import { EntityTableSettings } from "./list/EntityTableSettings";
import { ListSearchBox } from "./list/ListSearchBox";
import { ListToolbar } from "./list/ListToolbar";
import { useEntityListData, type RowRecord } from "./list/useEntityListData";
import { useListColumns } from "./list/useListColumns";
import { listPrefsKey } from "./listPreferences";
import { useRowActions } from "./useRowActions";
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
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
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
  // What the place this list is embedded in adds to it: a control next to « Créer » (a Documents tab's
  // « Associer »), and row actions after the entity's own.
  toolbarExtra?: ReactNode;
  extraRowActions?: RowActionDef<unknown>[];
}


/** A dismissable line under the toolbar: what a row action just did, or why it failed. */
function ActionMessage({ severity, text, onClose }: { severity: "success" | "error"; text: string; onClose: () => void }) {
  return (
    <Message
      severity={severity}
      className="entity-list-panel-action-error"
      content={
        <span className="sia-filled-row">
          <span className="sia-grow">{text}</span>
          <Button icon="bi bi-x" text rounded size="small" aria-label="Fermer" onClick={onClose} />
        </span>
      }
    />
  );
}

/**
 * Generic entity list — entirely driven by the registry (plan §3) plus, where supplied, an
 * entity's field catalog (`config.list.schema`). No entity-specific branching here:
 * entities/<type>/config.tsx supplies pinned columns via `config.list.columns` and, optionally, a
 * dynamic column source via `config.list.schema`. This component only wires the pieces together:
 * list/useEntityListData (what is fetched), list/useListColumns (the columns), list/ListToolbar,
 * list/EntityTableSettings, list/ListSearchBox and list/EntityTable (what is drawn).
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
  toolbarExtra,
  extraRowActions,
}: EntityListPanelProps) {
  const config = getEntityType(entityType) as EntityTypeConfig<unknown, unknown> | undefined;
  // Where this list's column and action-bar arrangement is remembered (listPreferences.ts).
  const prefsKey = listPrefsKey(entityType, scope, organizationId);
  const queryClient = useQueryClient();
  const writeMode = useWriteMode();

  const { table, catalog, hasSchema, pinnedFieldIds, params, page, rowRules, collectionTotal } = useEntityListData({
    entityType,
    config,
    organizationId,
    scope,
    prefsKey,
    embedded,
  });
  const { state, setPage, setSort, setSearch, setVisibleColumns, setFilters } = table;
  const { rows, totalCount, isLoading, isFetching, error } = page;
  const { columns, fieldByColumn, togglerOptions, filterSpecs } = useListColumns({
    config,
    catalog,
    visibleColumns: state.visibleColumns,
    pinnedFieldIds,
    organizationId,
    scope,
  });

  const [selectedRows, setSelectedRows] = useState<RowRecord[]>([]);
  const clearSelection = useCallback(() => setSelectedRows([]), []);

  // Created and duplicated entities open where a row's own identifier would (the overview).
  const rowActions = useRowActions({
    entityType,
    config,
    organizationId,
    writeMode,
    onOpen: onOpenOverview ?? onNavigate,
    prefsKey,
    extraRowActions,
  });

  // An organization-wide list of a kind created inside a project: its create form picks the project,
  // so "Créer" needs at least one project where the caller may create this kind.
  const createNeedsPickedProject = config?.list.createProjectKind != null && scopeProjectId(scope) == null;
  const creatableProjects = useQuery({
    queryKey: queryKeys.creatableProjects(config?.list.createProjectKind, organizationId),
    queryFn: () => searchCreatableProjects(organizationId!, config!.list.createProjectKind!, undefined, 1),
    enabled: createNeedsPickedProject && organizationId != null && creatable !== false,
  });
  const canCreateInSomeProject = (creatableProjects.data?.totalCount ?? 0) > 0;

  // Labels for the ids an "in" filter carries — FilterValue itself is ids-only (the wire shape),
  // so the chip bar needs this to print "Statut: En cours" rather than "Statut: 4711".
  const [filterOptionsByKey, setFilterOptionsByKey] = useState<Record<string, FilterOption[]>>({});
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
  function onCellSaved(row: RowRecord) {
    void queryClient.invalidateQueries({ queryKey: queryKeys.entityList(entityType) });
    if (row.id != null) void queryClient.invalidateQueries({ queryKey: queryKeys.entityDetail(entityType, row.id) });
  }

  if (!config) {
    return <div className="entity-list-panel-unsupported">Unknown entity type &quot;{entityType}&quot;</div>;
  }

  const hasActionBarSettings = rowActions.items.length > 0;
  const hasGear = hasSchema || hasActionBarSettings;

  const body = (
    <>
      {(config.list.searchable || onCreate || config.list.createForm || hasGear || filterSpecs.length > 0) && (
        <ListToolbar
          entityType={entityType}
          config={config}
          organizationId={organizationId}
          scope={scope}
          isLoading={isLoading}
          totalCount={totalCount}
          selectedCount={selectedRows.length}
          onClearSelection={clearSelection}
          creatable={creatable}
          createPrefill={createPrefill}
          endExtra={toolbarExtra}
          createBlocked={
            createNeedsPickedProject && !canCreateInSomeProject ? { loading: creatableProjects.isLoading } : null
          }
          onCreate={onCreate}
          // Opens in the overview, so the list stays where it was; the full fiche is one click away
          // (the overview's focus button).
          onCreated={(id) => (onOpenOverview ?? onNavigate)?.(entityType, id)}
          // Gear (table settings) then search then the filter chips on the left, create on the
          // right. The gear leads because it acts on the table's shape, which the search box and
          // filters do not.
          start={
            <>
              <EntityTableSettings
                columns={
                  hasSchema ? { options: togglerOptions, visible: state.visibleColumns, onChange: setVisibleColumns } : null
                }
                actionBar={
                  hasActionBarSettings
                    ? { items: rowActions.items, layout: rowActions.actionBar, onChange: rowActions.setActionBar }
                    : null
                }
              />
              {config.list.searchable && <ListSearchBox value={state.search ?? ""} onSearch={setSearch} />}
              {/* The filters narrow the same result set as the search box, so they sit right after
                  it, in the toolbar — not in a strip of their own above the columns. Rendered only
                  when there is at least one filterable column. */}
              {filterSpecs.length > 0 && (
                <FilterChipBar specs={filterSpecs} filters={state.filters} optionsByKey={filterOptionsByKey} onChange={onFilterChange} />
              )}
            </>
          }
        />
      )}
      {error && <div className="entity-list-panel-error">{(error as Error).message}</div>}
      {rowActions.notice && <ActionMessage severity="success" text={rowActions.notice} onClose={rowActions.clearNotice} />}
      {/* A page or a column arriving: the previous page stays on screen meanwhile (and a new
          column shows skeleton cells), so this is what says something is loading. Same convention as the
          JSF panels' own p:progressBar (panelContent.xhtml's panel-progressbar): a 3px track that
          is always there, so showing it never shifts the table, animated only while loading.
          Not during the very first load — the table's own loading overlay covers that. */}
      <ProgressBar
        mode="indeterminate"
        className={`panel-progressbar entity-list-panel-progressbar${isFetching && !isLoading ? " is-active" : ""}`}
        aria-hidden={!(isFetching && !isLoading)}
      />
      <EntityTable
        entityType={entityType}
        config={config}
        organizationId={organizationId}
        columns={columns}
        fieldByColumn={fieldByColumn}
        visibleColumns={state.visibleColumns}
        setVisibleColumns={setVisibleColumns}
        rows={rows}
        isLoading={isLoading}
        totalCount={totalCount}
        params={params}
        offset={state.offset}
        limit={state.limit}
        search={state.search}
        sort={state.sort}
        onPage={setPage}
        onSort={setSort}
        rowRules={rowRules}
        renderRowActions={rowActions.render}
        selectedRows={selectedRows}
        onSelectionChange={setSelectedRows}
        onNavigate={onNavigate}
        onOpenOverview={onOpenOverview}
        overviewEntityId={overviewEntityId}
        onCellSaved={onCellSaved}
      />
      {rowActions.dialog}
    </>
  );

  if (embedded) {
    // No <Panel>/PanelHeaderBar — the flex chain that gives scrollHeight="flex" its bound
    // otherwise comes from that <Panel>; reproduce it directly (styles/main-panel.css has the
    // matching `.entity-list-panel.embedded` rule for the tab body wrapping this).
    return (
      <div className="entity-list-panel embedded">
        {body}
      </div>
    );
  }

  return (
    // panel/header/actionUnitListPanelHeader.xhtml: icon + title + a count chip, plus the
    // generic toolbar — one single PanelHeaderBar passed as this <Panel>'s `header`, matching the
    // real markup's one sideview-titlebar div rather than a separate strip above this component.
    <Panel
      className="entity-list-panel"
      header={
        <PanelHeaderBar
          title={
            <div className="entity-list-panel-header sia-hstack">
              <i className={`${config.icon} sia-list-title-icon`} />
              <span className="sia-list-title-text">{config.labels.plural}</span>
              {/* *ListPanelHeader.xhtml's count chip: `<entity>-count-chip` (themed-panel's chip rule),
                  with the same inline transparent background/main-color text. */}
              {isLoading || collectionTotal == null ? (
                <Skeleton width="2.5rem" height="1.75rem" borderRadius="16px" className="loading-skeleton" />
              ) : (
                <Chip
                  label={String(collectionTotal)}
                  className={config.panelClass ? config.panelClass.replace(/-panel$/, "-count-chip") : undefined}
                  style={{ background: "transparent", color: "var(--main-color)" }}
                />
              )}
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
