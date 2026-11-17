import {type CSSProperties, type ReactNode, useEffect, useMemo, useRef, useState} from "react";
import {PaneStateScope, usePaneState} from "./pane/paneState";
import {type QueryClient, useQuery, useQueryClient} from "@tanstack/react-query";
import {Skeleton} from "primereact/skeleton";
import {Panel} from "primereact/panel";
import {Chip} from "primereact/chip";
import {Button} from "primereact/button";
import {getEntityType} from "../entities/registry";
import {scopeProjectId} from "../entities/shared/scope";
import {searchCreatableProjects} from "../entities/shared/creatableProjects";
import type {
    CreatePrefill,
    EntityPreview,
    EntityTypeConfig,
    FilterValue,
    ListScope,
    RowActionDef,
} from "../entities/shared/types";
import {PanelHeaderBar} from "../components/layout/PanelHeaderBar";
import {FilterChipBar} from "../components/table/FilterChipBar";
import {withLabels} from "../components/table/ColumnFilter";
import type {FilterOption} from "../fields/optionSources";
import {entityColor} from "../fields/display";
import type {FieldResource} from "../fields/types";
import type {PanelToolbarSlot} from "../mountOptions";
import {queryKeys} from "../api/queryKeys";
import {EntityCardGrid} from "./list/EntityCardGrid";
import {EntityMapView} from "./list/EntityMapView";
import {
  filtersForGeometryChange,
  mapGeometryFilter,
  mapGeometryOptions,
  type MapGeometryOption,
  resolveMapGeometry,
} from "./list/map/mapGeometry";
import {EntityTable} from "./list/EntityTable";
import {type EntityTableTree, useTreeRows} from "./list/useTreeRows";
import {EntityTableSettings, type EntityTableSettingsProps} from "./list/EntityTableSettings";
import {type ListViewMode, viewOptions} from "./list/listViews";
import {ViewActions} from "./list/ViewActions";
import {ViewTitle} from "./list/ViewTitle";
import {fixedTypeOf} from "./list/fixedType";
import {toViewState} from "./list/listState";
import {useNotify, useToastOnChange} from "../notify/NotifyProvider";
import {t} from "../i18n";
import {useFicheTabsActions} from "./detail/ficheTabsContext";
import {listPath} from "../navigation/paths";
import {ListSearchOverlayBox} from "./list/ListSearchOverlay";
import {ListToolbar} from "./list/ListToolbar";
import {BulkActionBar} from "./list/bulk/BulkActionBar";
import {type ListSelection, useListSelection} from "./list/bulk/useListSelection";
import {type SelectionGuard, useSelectionGuard} from "./list/bulk/useSelectionGuard";
import {type RowRecord, useEntityListData} from "./list/useEntityListData";
import {useListColumns} from "./list/useListColumns";
import {defaultOpenMode, listPrefsKey, loadListPrefs, type OpenMode, saveListPrefs} from "./list/listPreferences";
import {useRowActions} from "./list/useRowActions";
import {useWriteMode} from "./pane/writeMode";
import type {ListSeed} from "../navigation/externalBus";

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
  onOpenOverview?: (
    entityType: string,
    id: string | number,
    preview?: EntityPreview,
  ) => void;
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
  // straight into ListParams.scope — see entities/shared/listApi.ts for the URL it produces.
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
  // The search the list was opened with (the top search bar's « Voir tout »). A new `nonce` applies
  // it again: the list is already open and must follow the new search, whatever was typed since.
  seed?: ListSeed;
  // The saved view the list is on (`?viewId=` of its address), and how to open another (or, without an
  // id, the default list). Without `onViewChange` the list offers no views.
  viewId?: number;
  onViewChange?: (viewId?: number) => void;
  // A configured tab of a fiche (see panels/detail/ficheConfig.ts): the filters that define it, and the key that
  // keeps its state apart from the fiche's other lists of this type.
  fixedFilters?: Record<string, FilterValue>;
  stateKey?: string;
  // The preview of a tab being configured (panels/detail/FicheTabsEditor.tsx): its state is not kept for the user, it
  // opens on `initialFilters`, and `onFiltersChange` hears every change of its filters.
  volatile?: boolean;
  initialFilters?: Record<string, FilterValue>;
  onFiltersChange?: (filters: Record<string, FilterValue>) => void;
}

type ListConfig = EntityTypeConfig;
type ListData = ReturnType<typeof useEntityListData>;
type TableControls = ListData["table"];
type ListColumns = ReturnType<typeof useListColumns>;
type RowActions = ReturnType<typeof useRowActions>;
type FilterControls = ReturnType<typeof useFilterControls>;
type Filters = Record<string, FilterValue>;
type FilterOptionsByKey = Record<string, FilterOption[]>;
type ListPrefsPatch = Parameters<typeof saveListPrefs>[1];
type SavePrefs = (patch: ListPrefsPatch) => void;
type NavigateFn = NonNullable<EntityListPanelProps["onNavigate"]>;
type OpenOverviewFn = NonNullable<EntityListPanelProps["onOpenOverview"]>;
// Set when the create form needs a project picked and none accepts this kind: "Créer" is disabled.
type CreateBlocked = { loading: boolean } | null;

// *ListPanelHeader.xhtml's count chip: inline transparent background and main-color text.
const COUNT_CHIP_STYLE: CSSProperties = { background: "transparent", color: "var(--main-color)" };

/**
 * Generic entity list — entirely driven by the registry (plan §3) plus, where supplied, an
 * entity's field catalog (`config.list.schema`). No entity-specific branching here:
 * entities/<type>/config.tsx supplies pinned columns via `config.list.columns` and, optionally, a
 * dynamic column source via `config.list.schema`. This component only wires the pieces together:
 * list/useEntityListData (what is fetched), list/useListColumns (the columns), list/ListToolbar,
 * list/EntityTableSettings, list/ListSearchOverlay and list/EntityTable (what is drawn).
 */
export function EntityListPanel(props: EntityListPanelProps) {
  // Each view keeps its own state in the pane store: moving between views is not a change of the list
  // being shown, but of which list this is.
  return props.viewId != null ? (
    <PaneStateScope name={`view:${props.viewId}`}>
      <EntityListPanelBody {...props} />
    </PaneStateScope>
  ) : (
    <EntityListPanelBody {...props} />
  );
}

function prefillWithIdIfSet(fixedTypeId: string | undefined, createPrefill: CreatePrefill | undefined) {
  return fixedTypeId ? {...createPrefill, typeId: fixedTypeId} : createPrefill;
}

/**
 * Wires the list's data, preferences and selection together, then lays out the toolbar, the bulk
 * action bar and the current view (map, cards or table), inside a panel unless embedded.
 */
function EntityListPanelBody({
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
  seed,
  viewId,
  onViewChange,
  fixedFilters,
  stateKey,
  volatile,
  initialFilters,
  onFiltersChange,
}: Readonly<EntityListPanelProps>) {
  const config = getEntityType(entityType) as ListConfig | undefined;
  // Where this list's column and action-bar arrangement is remembered (listPreferences.ts).
  const prefsKey = listPrefsKey(entityType, scope, organizationId);
  const queryClient = useQueryClient();
  const writeMode = useWriteMode();

  const listData = useEntityListData({
    entityType,
    config,
    organizationId,
    scope,
    prefsKey,
    embedded,
    viewId,
    fixedFilters,
    stateKey,
    volatile,
    initialFilters,
  });
  const { table, catalog, params, page, viewMode, listReady, viewMissing } = listData;
  const { state } = table;
  const showTree = viewMode === "tree";

  const { prefs, savePrefs } = useListPrefs(prefsKey);
  // How a row opens a page: in the side overview, or in the main pane (focus) by leaving the overview out.
  // Project lists start in focus, the others in the overview.
  const openMode = prefs.openMode ?? defaultOpenMode(entityType);
  const rowOpenOverview = openMode === "focus" ? undefined : onOpenOverview;
  // Created and duplicated entities, and rows opened from the search overlay, open where a row's own
  // identifier would (the overview), whatever the open mode.
  const openEntity: OpenOverviewFn | undefined = onOpenOverview ?? onNavigate;
  // The geometry column the map places the rows by: one of the catalog's geometry columns, the entity's own by default.
  const geometryOptions = useMemo(() => mapGeometryOptions(catalog?.fields), [catalog]);
  const mapGeometry = resolveMapGeometry(prefs.mapGeometry, geometryOptions);
  // A tab filtered on exactly one type creates entities of that type.
  const fixedTypeId = fixedTypeOf(fixedFilters, catalog);
  const createPrefillWithType = prefillWithIdIfSet(fixedTypeId, createPrefill);

  useFiltersListener(state.filters, onFiltersChange);
  useSeedSearch(seed, table);

  const treeRows = useTreeRows({
    entityType,
    config,
    organizationId,
    params,
    fields: state.visibleColumns,
    offset: state.offset,
    limit: state.limit,
    enabled: showTree && listReady,
  });
  // The tree view fetches its own rows; every other view shows the flat page.
  const { rows, totalCount, isLoading, error } = showTree ? treeRows : page;
  const listColumns = useListColumns({
    config,
    catalog,
    visibleColumns: state.visibleColumns,
    pinnedFieldIds: listData.pinnedFieldIds,
    organizationId,
    scope,
  });
  // The rows ticked, or the whole result set but the rows unticked since (the header's box): what
  // the bulk action bar acts on.
  const selection = useListSelection({ config, params, rows, totalCount });
  // A search or a filter changes the rows: applied over a selection only once the user agrees to lose it.
  const selectionGuard = useSelectionGuard(selection);
  const filterControls = useFilterControls(table, selectionGuard);
  const mapFilter = mapFilterControls({
    filters: state.filters,
    mapGeometry,
    geometryOptions,
    fields: catalog?.fields,
    filterControls,
  });
  const rowActions = useRowActions({
    entityType,
    config,
    organizationId,
    writeMode,
    onOpen: openEntity,
    prefsKey,
    extraRowActions,
  });
  const createBlocked = useCreateBlocked({ config, scope, organizationId, creatable });

  useToastOnChange(error ? (error as Error).message : null);
  useToastOnChange(showTree ? treeRows.notice : null, "info");
  useMissingViewFallback(viewId, viewMissing, onViewChange);

  if (!config) {
    return (
      <div className="entity-list-panel-unsupported">
        Unknown entity type &quot;{entityType}&quot;
      </div>
    );
  }

  // A preview being configured has no row actions to arrange.
  const hasActionBarSettings = rowActions.items.length > 0 && !volatile;
  // Relation tabs pass `toolbarExtra`; the entity's own list may declare `config.list.toolbarExtra`.
  const resolvedToolbarExtra = toolbarExtra ?? config.list.toolbarExtra;
  const showToolbar = hasToolbarContent({
    config,
    onCreate,
    hasGear: listData.hasSchema || hasActionBarSettings,
    filterCount: listColumns.filterSpecs.length,
    toolbarExtra: resolvedToolbarExtra,
  });
  const tableSettings = buildTableSettings({
    openMode: onOpenOverview && onNavigate ? openMode : null,
    mapGeometry: viewMode === "map" ? mapGeometry : undefined,
    geometryOptions,
    listData,
    listColumns,
    rowActions: hasActionBarSettings ? rowActions : null,
    savePrefs,
    mapFilter,
  });

  const body = (
    <>
      {showToolbar && (
        <EntityListToolbar
          entityType={entityType}
          config={config}
          organizationId={organizationId}
          scope={scope}
          listData={listData}
          filterSpecs={listColumns.filterSpecs}
          filterControls={filterControls}
          tableSettings={tableSettings}
          isLoading={isLoading}
          totalCount={showTree ? treeRows.matchCount : totalCount}
          selection={selection}
          creatable={creatable}
          createPrefill={createPrefillWithType}
          createBlocked={createBlocked}
          endExtra={
            <>
              {resolvedToolbarExtra}
              <SaveAsTabButton
                entityType={entityType}
                filters={params.filters}
                enabled={Boolean(embedded && scope && !volatile)}
              />
            </>
          }
          onCreate={onCreate}
          onOpen={openEntity}
        />
      )}
      {/* Laid over the top of the table while rows are ticked (only to someone who may write). */}
      {writeMode && !volatile && (
        <BulkActionBar
          entityType={entityType}
          config={config}
          organizationId={organizationId}
          catalog={catalog}
          selection={selection}
          rowRules={listData.rowRules}
        />
      )}
      <ListLayout
        entityType={entityType}
        config={config}
        organizationId={organizationId}
        viewMode={viewMode}
        listData={listData}
        rows={rows}
        isLoading={isLoading}
        totalCount={totalCount}
        listColumns={listColumns}
        selection={selection}
        rowActions={rowActions}
        tree={showTree ? treeRows.tree : undefined}
        geometry={mapGeometry?.value}
        onNavigate={onNavigate}
        onOpenOverview={rowOpenOverview}
        overviewEntityId={overviewEntityId}
        onCellSaved={(row) => refreshSavedRow(queryClient, entityType, row)}
        readOnly={volatile}
      />
      {rowActions.dialog}
      {selectionGuard.dialog}
    </>
  );

  const rowEntityStyle = rowEntityStyleOf(entityType);

  if (embedded) {
    // No <Panel>/PanelHeaderBar — the flex chain that gives scrollHeight="flex" its bound
    // otherwise comes from that <Panel>; reproduce it directly (styles/main-panel.css has the
    // matching `.entity-list-panel.embedded` rule for the tab body wrapping this).
    return (
      <div className="entity-list-panel embedded" style={rowEntityStyle}>
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
      style={rowEntityStyle}
      header={
        <ListPanelHeader
          entityType={entityType}
          config={config}
          organizationId={organizationId}
          scope={scope}
          viewId={viewId}
          listData={listData}
          isLoading={isLoading}
          toolbar={toolbar}
          onViewChange={onViewChange}
        />
      }
    >
      {body}
    </Panel>
  );
}

// ─── Hooks ──────────────────────────────────────────────────────────────────────────────────────

/**
 * What is remembered for the kind of list in this browser (listPreferences), and how to change it.
 * The preferences live in localStorage, which React does not watch: a tick forces the re-render.
 */
function useListPrefs(prefsKey: string) {
  const [, setPrefsTick] = useState(0);
  const savePrefs: SavePrefs = (patch) => {
    saveListPrefs(prefsKey, patch);
    setPrefsTick((n) => n + 1);
  };
  return { prefs: loadListPrefs(prefsKey), savePrefs };
}

/**
 * Tells `onFiltersChange` every change of the list's filters (a tab preview being configured hears its
 * filters). The listener is read through a ref so a new callback identity does not re-fire the effect.
 */
function useFiltersListener(filters: Filters, onFiltersChange?: (filters: Filters) => void) {
  const filtersListener = useRef(onFiltersChange);
  filtersListener.current = onFiltersChange;
  useEffect(() => {
    filtersListener.current?.(filters);
  }, [filters]);
}

/**
 * Applies the search the list was opened with (the top search bar's « Voir tout »), replacing any filter.
 */
function useSeedSearch(seed: ListSeed | undefined, table: TableControls) {
  // Keyed on the nonce alone: the seed object is rebuilt by its owner, the search it carries is not
  // a reason to apply it again (the user may have edited it since).
  const seedNonce = seed?.nonce;
  const seedSearch = seed?.search;
  useEffect(() => {
    if (seedNonce == null) return;
    table.setFilters({});
    table.setSearch(seedSearch ?? "");
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seedNonce]);
}

/**
 * The list's filter changes (a chip, or the search overlay's « Appliquer »), each applied through the
 * selection guard, and the labels of the ids an "in" filter carries.
 */
function useFilterControls(table: TableControls, selectionGuard: SelectionGuard) {
  // FilterValue itself is ids-only (the wire shape), so the chip bar needs these labels to print
  // "Statut: En cours" rather than "Statut: 4711". Kept in the pane store to survive a remount.
  const [optionsByKey, setOptionsByKey] = usePaneState<FilterOptionsByKey>("filterOptions", {});

  const changeFilter = (key: string, value: FilterValue | undefined, options?: FilterOption[]) =>
    selectionGuard.guard(() => {
      table.setFilters(withFilter(table.state.filters, key, value, options));
      if (options) setOptionsByKey((byKey) => ({ ...byKey, [key]: options }));
    });

  // Several filters at once (one moved from a key to another), in a single change.
  const replaceFilters = (filters: Filters) => selectionGuard.guard(() => table.setFilters(filters));

  const applySearch = (search: string, filters: Filters, options: FilterOptionsByKey) =>
    selectionGuard.guard(() => {
      table.setFilters(withLabelsByKey(filters, options));
      table.setSearch(search);
      if (Object.keys(options).length > 0) setOptionsByKey((byKey) => ({ ...byKey, ...options }));
    });

  return { optionsByKey, changeFilter, replaceFilters, applySearch };
}

/**
 * What the map does to the filters: switching to it filters the geometry it draws on « n'est pas vide », a
 * chip like any other, which follows the geometry when another one is drawn.
 */
function mapFilterControls({
  filters,
  mapGeometry,
  geometryOptions,
  fields,
  filterControls,
}: {
  filters: Filters;
  mapGeometry: MapGeometryOption | undefined;
  geometryOptions: MapGeometryOption[];
  fields: Record<string, FieldResource> | undefined;
  filterControls: ReturnType<typeof useFilterControls>;
}): MapFilterControls {
  return {
    onShowMap: () => {
      const filter = mapGeometryFilter(filters, mapGeometry, fields);
      if (filter && mapGeometry) filterControls.changeFilter(mapGeometry.value, filter);
    },
    onGeometryChange: (value) => {
      const drawn = geometryOptions.find((o) => o.value === value);
      const next = filtersForGeometryChange(filters, mapGeometry, drawn, fields);
      if (next) filterControls.replaceFilters(next);
    },
  };
}

/**
 * Whether "Créer" must be disabled. An organization-wide list of a kind created inside a project has
 * a create form that picks the project, so it needs at least one project where the caller may create
 * this kind; only one result is fetched, the count is all that matters.
 */
function useCreateBlocked({
  config,
  scope,
  organizationId,
  creatable,
}: {
  config: ListConfig | undefined;
  scope?: ListScope;
  organizationId?: number;
  creatable?: boolean;
}): CreateBlocked {
  const createProjectKind = config?.list.createProjectKind;
  const needsPickedProject = createProjectKind != null && scopeProjectId(scope) == null;
  const creatableProjects = useQuery({
    queryKey: queryKeys.creatableProjects(createProjectKind, organizationId),
    queryFn: () => searchCreatableProjects(organizationId!, createProjectKind!, undefined, 1),
    enabled: needsPickedProject && organizationId != null && creatable !== false,
  });
  const canCreateInSomeProject = (creatableProjects.data?.totalCount ?? 0) > 0;
  return needsPickedProject && !canCreateInSomeProject ? { loading: creatableProjects.isLoading } : null;
}

/**
 * A view that no longer exists (deleted since a bookmark or a link to it was made): says so and falls
 * back to the default list.
 */
function useMissingViewFallback(
  viewId: number | undefined,
  viewMissing: boolean,
  onViewChange?: (viewId?: number) => void,
) {
  const notify = useNotify();
  useEffect(() => {
    if (viewId == null || !viewMissing) return;
    notify.error(t("views.missing"));
    onViewChange?.(undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [viewId, viewMissing]);
}

// ─── Pure helpers ───────────────────────────────────────────────────────────────────────────────

/**
 * The filters with `key` set to `value` (labelled from `options`), or removed when `value` is absent.
 */
function withFilter(
  filters: Filters,
  key: string,
  value: FilterValue | undefined,
  options?: FilterOption[],
): Filters {
  const next = { ...filters };
  if (value) next[key] = withLabels(value, options);
  else delete next[key];
  return next;
}

/**
 * Each filter labelled from the options picked for its own key.
 */
function withLabelsByKey(filters: Filters, options: FilterOptionsByKey): Filters {
  return Object.fromEntries(Object.entries(filters).map(([key, value]) => [key, withLabels(value, options[key])]));
}

/**
 * Re-fetches the list (so the saved row's new value shows without a full page reload) and, in case
 * the same entity is open in a detail view or the overview pane, that too — the overlay itself
 * doesn't know which other views might be showing this row.
 */
function refreshSavedRow(queryClient: QueryClient, entityType: string, row: RowRecord) {
  void queryClient.invalidateQueries({ queryKey: queryKeys.entityList(entityType) });
  if (row.id != null) void queryClient.invalidateQueries({ queryKey: queryKeys.entityDetail(entityType, row.id) });
}

/**
 * Whether the toolbar has anything to show: a search box, a create button, the gear, filter chips or
 * a control added by the embedding place.
 */
function hasToolbarContent({
  config,
  onCreate,
  hasGear,
  filterCount,
  toolbarExtra,
}: {
  config: ListConfig;
  onCreate?: () => void;
  hasGear: boolean;
  filterCount: number;
  toolbarExtra: ReactNode;
}): boolean {
  return Boolean(
    config.list.searchable || onCreate || config.list.createForm || hasGear || filterCount > 0 || toolbarExtra,
  );
}

/**
 * The gear's sections; each is null when the list has nothing to set there.
 */
function buildTableSettings({
  openMode,
  mapGeometry,
  geometryOptions,
  listData,
  listColumns,
  rowActions,
  savePrefs,
  mapFilter,
}: {
  // Null when a row cannot open both in the overview and in the main pane.
  openMode: OpenMode | null;
  // Undefined when the list is not shown as a map or has no geometry column.
  mapGeometry: MapGeometryOption | undefined;
  geometryOptions: MapGeometryOption[];
  listData: ListData;
  listColumns: ListColumns;
  // Null when there are no row actions to arrange.
  rowActions: RowActions | null;
  savePrefs: SavePrefs;
  mapFilter: MapFilterControls;
}): EntityTableSettingsProps {
  const { table, views, viewMode, hasSchema } = listData;
  return {
    openMode: openMode ? { value: openMode, onChange: (mode) => savePrefs({ openMode: mode }) } : null,
    // Shown even for a single column: it says which geometry the map draws.
    mapGeometry: mapGeometry ? mapGeometrySetting(mapGeometry, geometryOptions, savePrefs, mapFilter.onGeometryChange) : null,
    views: views.length > 1 ? viewsSetting(views, viewMode, table, mapFilter.onShowMap) : null,
    columns: hasSchema
      ? { options: listColumns.togglerOptions, visible: table.state.visibleColumns, onChange: table.setVisibleColumns }
      : null,
    actionBar: rowActions
      ? { items: rowActions.items, layout: rowActions.actionBar, onChange: rowActions.setActionBar }
      : null,
  };
}

/**
 * The gear's "Géométrie affichée" section: the chosen column is remembered in the list preferences.
 */
function mapGeometrySetting(
  mapGeometry: MapGeometryOption,
  geometryOptions: MapGeometryOption[],
  savePrefs: SavePrefs,
  onGeometryChange: (value: string) => void,
): EntityTableSettingsProps["mapGeometry"] {
  return {
    options: geometryOptions.map((o) => ({ mode: o.value, label: o.label, icon: o.icon })),
    value: mapGeometry.value,
    onChange: (value) => {
      savePrefs({ mapGeometry: value });
      onGeometryChange(value);
    },
  };
}

/**
 * What the map does to the filters: « <geometry> n'est pas vide » added when the user switches to it, and
 * moved along when they pick another geometry to draw.
 */
interface MapFilterControls {
  onShowMap: () => void;
  onGeometryChange: (value: string) => void;
}

/**
 * The gear's view switch (list, tree, cards, map); `onShowMap` runs when it switches to the map.
 */
function viewsSetting(
  views: ListViewMode[],
  viewMode: ListViewMode,
  table: TableControls,
  onShowMap: () => void,
): EntityTableSettingsProps["views"] {
  return {
    options: viewOptions(views),
    value: viewMode,
    onChange: (mode) => {
      table.setViewMode(mode);
      // A page of roots is not a page of the flat list: back to the first.
      table.setPage(0, table.state.limit);
      if (mode === "map" && viewMode !== "map") onShowMap();
    },
  };
}

/**
 * The pane's toolbar. On a view, the pane's bookmark is the view's: its address carries ?viewId=, its
 * title is the view's. Whether it is bookmarked is not known from the page that mounted (that was the
 * list's): it is asked afresh.
 */
function viewToolbarOf(
  toolbar: PanelToolbarSlot | undefined,
  entityType: string,
  viewId: number | undefined,
  viewTitle: string | undefined,
): PanelToolbarSlot | undefined {
  const viewListPath = listPath(entityType, viewId);
  if (!toolbar || viewId == null || viewListPath == null) return toolbar;
  return { ...toolbar, chrome: { resourceUri: viewListPath, title: viewTitle ?? toolbar.chrome.title } };
}

/**
 * Exposes the entity type's color to the rows' CSS (`--row-entity-color`), when it has one.
 */
function rowEntityStyleOf(entityType: string): CSSProperties | undefined {
  const rowEntityColor = entityColor(entityType);
  return rowEntityColor ? ({ "--row-entity-color": rowEntityColor } as CSSProperties) : undefined;
}

/**
 * `<entity>-count-chip` (themed-panel's chip rule), derived from the entity's `<entity>-panel` class.
 */
function countChipClassOf(panelClass: string | undefined): string | undefined {
  return panelClass ? panelClass.replace(/-panel$/, "-count-chip") : undefined;
}

// ─── Components ─────────────────────────────────────────────────────────────────────────────────

interface EntityListToolbarProps {
  entityType: string;
  config: ListConfig;
  organizationId?: number;
  scope?: ListScope;
  listData: ListData;
  filterSpecs: ListColumns["filterSpecs"];
  filterControls: FilterControls;
  tableSettings: EntityTableSettingsProps;
  isLoading: boolean;
  totalCount: number;
  selection: ListSelection;
  creatable?: boolean;
  createPrefill?: CreatePrefill;
  createBlocked: CreateBlocked;
  endExtra: ReactNode;
  onCreate?: () => void;
  onOpen?: OpenOverviewFn;
}

/**
 * The list's toolbar: gear (table settings) then search then the filter chips on the left, create on
 * the right. The gear leads because it acts on the table's shape, which the search box and filters do not.
 */
function EntityListToolbar({
  entityType,
  config,
  organizationId,
  scope,
  listData,
  filterSpecs,
  filterControls,
  tableSettings,
  isLoading,
  totalCount,
  selection,
  creatable,
  createPrefill,
  createBlocked,
  endExtra,
  onCreate,
  onOpen,
}: Readonly<EntityListToolbarProps>) {
  const { state } = listData.table;
  return (
    <ListToolbar
      entityType={entityType}
      config={config}
      organizationId={organizationId}
      scope={scope}
      isLoading={isLoading}
      totalCount={totalCount}
      selectedCount={selection.count}
      onClearSelection={selection.clear}
      creatable={creatable}
      createPrefill={createPrefill}
      endExtra={endExtra}
      createBlocked={createBlocked}
      onCreate={onCreate}
      // Opens in the overview, so the list stays where it was; the full fiche is one click away
      // (the overview's focus button).
      onCreated={(id) => onOpen?.(entityType, id, { tab: "fiche" })}
      startFilters={
        filterSpecs.length > 0 ? (
          <FilterChipBar
            specs={filterSpecs}
            filters={state.filters}
            optionsByKey={filterControls.optionsByKey}
            onChange={filterControls.changeFilter}
          />
        ) : undefined
      }
      start={
        <>
          <EntityTableSettings {...tableSettings} />
          {config.list.searchable && (
            <ListSearchOverlayBox
              entityType={entityType}
              config={config}
              params={listData.params}
              filters={state.filters}
              filterSpecs={filterSpecs}
              optionsByKey={filterControls.optionsByKey}
              value={state.search ?? ""}
              onApply={filterControls.applySearch}
              onFilterRemove={(key) => filterControls.changeFilter(key, undefined)}
              onOpenRow={(id) => onOpen?.(entityType, id)}
            />
          )}
        </>
      }
    />
  );
}

interface SaveAsTabButtonProps {
  entityType: string;
  filters: Filters | undefined;
  // Only an embedded, scoped list that is not a tab preview can become a tab of its own.
  enabled: boolean;
}

/**
 * Keeps a list shown as a tab of a fiche, filters included, as a tab of the project. Renders nothing
 * when the user may not configure the fiche (no FicheTabs context) or the list has no filters to keep.
 */
function SaveAsTabButton({ entityType, filters, enabled }: Readonly<SaveAsTabButtonProps>) {
  const ficheTabs = useFicheTabsActions();
  if (!ficheTabs || !enabled || !filters) return null;
  return (
    <Button
      icon="bi bi-pin-angle"
      text
      rounded
      size="large"
      aria-label={t("tabs.saveAsTab")}
      tooltip={t("tabs.saveAsTab")}
      onClick={() => ficheTabs.saveAsTab(entityType, filters)}
    />
  );
}

interface ListLayoutProps {
  entityType: string;
  config: ListConfig;
  organizationId?: number;
  viewMode: ListViewMode;
  listData: ListData;
  rows: RowRecord[];
  isLoading: boolean;
  totalCount: number;
  listColumns: ListColumns;
  selection: ListSelection;
  rowActions: RowActions;
  // Set in tree mode only: the table then draws the rows as a tree.
  tree?: EntityTableTree;
  geometry?: string;
  onNavigate?: NavigateFn;
  // Undefined in focus mode: a row then replaces the list instead of opening in the overview.
  onOpenOverview?: OpenOverviewFn;
  overviewEntityId?: string | number;
  onCellSaved: (row: RowRecord) => void;
  readOnly?: boolean;
}

/**
 * The rows, drawn the way the user chose: on a map, as cards, or as a table (flat or tree).
 */
function ListLayout({
  entityType,
  config,
  organizationId,
  viewMode,
  listData,
  rows,
  isLoading,
  totalCount,
  listColumns,
  selection,
  rowActions,
  tree,
  geometry,
  onNavigate,
  onOpenOverview,
  overviewEntityId,
  onCellSaved,
  readOnly,
}: Readonly<ListLayoutProps>) {
  const { table, params } = listData;
  const { state } = table;

  if (viewMode === "map") {
    return (
      <EntityMapView
        entityType={entityType}
        config={config}
        params={params}
        onNavigate={onNavigate}
        onOpenOverview={onOpenOverview}
        // In focus mode a row replaces the list: there is no overview beside the map to mirror.
        selectedId={onOpenOverview ? overviewEntityId : undefined}
        geometry={geometry}
      />
    );
  }

  if (viewMode === "cards") {
    return (
      <EntityCardGrid
        entityType={entityType}
        config={config}
        rows={rows}
        isLoading={isLoading}
        totalCount={totalCount}
        params={params}
        offset={state.offset}
        limit={state.limit}
        onPage={table.setPage}
        renderRowActions={rowActions.render}
        onNavigate={onNavigate}
        onOpenOverview={onOpenOverview}
        overviewEntityId={overviewEntityId}
        selectedRows={selection.rows}
        onSelectionChange={selection.setRows}
      />
    );
  }

  return (
    <EntityTable
      entityType={entityType}
      config={config}
      organizationId={organizationId}
      columns={listColumns.columns}
      fieldByColumn={listColumns.fieldByColumn}
      visibleColumns={state.visibleColumns}
      setVisibleColumns={table.setVisibleColumns}
      rows={rows}
      isLoading={isLoading}
      totalCount={totalCount}
      params={params}
      offset={state.offset}
      limit={state.limit}
      search={state.search}
      sort={state.sort}
      onPage={table.setPage}
      onScrollWindow={listData.onScrollWindow}
      onSort={table.setSort}
      rowRules={listData.rowRules}
      renderRowActions={rowActions.render}
      selectedRows={selection.rows}
      onSelectionChange={selection.setRows}
      allSelected={selection.all}
      onSelectAllChange={selection.setAll}
      onNavigate={onNavigate}
      onOpenOverview={onOpenOverview}
      overviewEntityId={overviewEntityId}
      onCellSaved={onCellSaved}
      readOnly={readOnly}
      tree={tree}
    />
  );
}

interface ListPanelHeaderProps {
  entityType: string;
  config: ListConfig;
  organizationId?: number;
  scope?: ListScope;
  viewId?: number;
  listData: ListData;
  isLoading: boolean;
  toolbar?: PanelToolbarSlot;
  onViewChange?: (viewId?: number) => void;
}

/**
 * The panel's title bar: title and count chip, the pane's toolbar, and the saved views' button.
 */
function ListPanelHeader({
  entityType,
  config,
  organizationId,
  scope,
  viewId,
  listData,
  isLoading,
  toolbar,
  onViewChange,
}: Readonly<ListPanelHeaderProps>) {
  const { view, table, collectionTotal, modified, hasUserState, resetToBase, userState } = listData;
  // Changed since it was opened: on a view, from the view's state; on the default list, anything the user kept.
  const viewsDirty = viewId != null ? modified : hasUserState;
  // Views are of the organization-wide list (the header is never shown on an embedded one), in a pane
  // that can open another view. Their button only shows once the list has been changed.
  const showViewActions = scope == null && organizationId != null && onViewChange != null && viewsDirty;

  return (
    <PanelHeaderBar
      title={
        <ListPanelTitle
          config={config}
          view={viewId != null ? view : undefined}
          organizationId={organizationId}
          isLoading={isLoading}
          collectionTotal={collectionTotal}
        />
      }
      toolbar={viewToolbarOf(toolbar, entityType, viewId, view?.title)}
      // The old titlebar's view button, at the right of the toolbar before the bookmark.
      toolbarExtra={
        showViewActions ? (
          <ViewActions
            entityType={entityType}
            organizationId={organizationId}
            activeView={view}
            currentViewState={() => toViewState(table.state, table.columnsSeeded)}
            onOpen={onViewChange}
            onReset={resetToBase}
            onUpdated={userState.reset}
          />
        ) : undefined
      }
    />
  );
}

interface ListPanelTitleProps {
  config: ListConfig;
  // The saved view the list is on, if any: its name replaces the type's plural label.
  view: ListData["view"];
  organizationId?: number;
  isLoading: boolean;
  collectionTotal: ListData["collectionTotal"];
}

/**
 * The entity type's icon, the list's name (the view's, or the type's plural label) and its count chip.
 */
function ListPanelTitle({ config, view, organizationId, isLoading, collectionTotal }: Readonly<ListPanelTitleProps>) {
  return (
    <div className="entity-list-panel-header sia-hstack">
      <i className={`${config.icon} sia-list-title-icon`} />
      {view && organizationId != null ? (
        <ViewTitle view={view} organizationId={organizationId} />
      ) : (
        <span className="sia-list-title-text">{config.labels.plural}</span>
      )}
      {isLoading || collectionTotal == null ? (
        <Skeleton width="2.5rem" height="1.75rem" borderRadius="16px" className="loading-skeleton" />
      ) : (
        <Chip label={String(collectionTotal)} className={countChipClassOf(config.panelClass)} style={COUNT_CHIP_STYLE} />
      )}
    </div>
  );
}
