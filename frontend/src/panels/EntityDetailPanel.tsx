import { isValidElement, useCallback, useEffect, useMemo, useRef, useState, type SyntheticEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Panel } from "primereact/panel";
import { TabView, TabPanel } from "primereact/tabview";
import { PaneErrorBoundary } from "../components/PaneErrorBoundary";
import { Skeleton } from "primereact/skeleton";
import { apiUrl } from "../api/basePath";
import { openProjectSettings, openTablesSettings, settingsTableOf } from "./projectSettings";
import { FieldConfigProvider } from "../fields/fieldConfigContext";
import { getEntityType } from "../entities/registry";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import { SiblingNav } from "../components/SiblingNav";
import { EntityBreadcrumbTrail } from "../components/EntityBreadcrumbTrail";
import { CreateEntityOverlay } from "../components/CreateEntityOverlay";
import type { PanelActions, PanelToolbarSlot } from "../mountOptions";
import { PaneStateScope, usePaneState } from "./paneState";
import { useCanEdit, useWriteMode } from "./writeMode";
import { ValidationStatusButton } from "../components/ValidationStatusButton";
import { DetailBodySkeleton } from "../components/DetailSkeleton";
import { recallListContext, rememberListContext, type ListContext } from "./listContext";
import { siblingsQuery } from "./listSiblings";
import { useSwipeSiblings } from "./useSwipeSiblings";

import type { DetailTabDef, EntityPreview, EntitySibling, FilterValue } from "../entities/types";
import { queryKeys } from "../api/queryKeys";
import { t } from "../i18n";
import { useDeleteEntity } from "./useDeleteEntity";
import { useUserViewState } from "./useUserViewState";
import { resolveTabs, useFicheConfig, ficheScopeOf } from "./ficheConfig";
import { FicheTabsProvider } from "./ficheTabsContext";
import { FicheTabsEditor } from "./FicheTabsEditor";
import { AddListTabDialog } from "./AddListTabDialog";
import { TabCount } from "./TabCount";
import { editableTabs, relationTargets, restorableTabs, restoreTab, type EditableTab } from "./ficheTabsModel";
import { addTabItems } from "./addTabMenu";
import { Menu } from "primereact/menu";
import { useFicheTabsSave } from "./useFicheTabsSave";
import { messageForError } from "../api/errors";
import { useNotify, useToastOnChange } from "../notify/NotifyProvider";
import { PersistedTabState } from "./persistedPaneState";

export type { EntityPreview };

/** The list context of a neighbour found by walking `current`'s list: same request, its own position. */
function neighbourContext(current: ListContext | undefined, sibling: EntitySibling): ListContext | undefined {
  return current && sibling.index != null ? { params: current.params, index: sibling.index } : undefined;
}

export interface EntityDetailPanelProps {
  entityType: string;
  entityId: string | number;
  // Omitted for the overview pane render call that has no toolbar to show, or once the main
  // pane has navigated away from the entity this toolbar was built for (App.tsx).
  toolbar?: PanelToolbarSlot;
  // Makes the breadcrumb's "Projets" crumb switch the pane to that entity's list, the same way
  // EntityListPanel's own row click navigates. Omitted for the overview pane's OWN breadcrumb
  // (there is no "list" render inside the overview for that crumb to switch to — JSF's own
  // breadcrumb there renders without driving anything either).
  //
  // This and the next three props are also forwarded into every tab's DetailTabHelpers (plan:
  // generic related-list tab) so a tab rendering an embedded EntityListPanel (relationTab) gets
  // exactly what App.tsx's own top-level list gets — no separate wiring per tab. `onOpenOverview`
  // and `overviewEntityId` ARE passed for the overview pane's own instance too (App.tsx): a
  // relationTab rendered there (e.g. a project's own UE list, shown while that project is itself
  // open in the overview) must still be able to retarget the overview on a row click, even though
  // the click originates from within the overview.
  onNavigate?: (entityType: string, id?: string | number) => void;
  organizationId?: number;
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  overviewEntityId?: string | number;
  // "Fiche précédente/suivante" (plan: prev/next navigation). Deliberately NOT `onNavigate`: that
  // callback also drives the breadcrumb's "Tous les <plural>" crumb, which must switch the PANE
  // this panel is in to a list — a sibling jump must instead stay a same-kind detail navigation
  // (the main pane's own client-side nav for the main pane's fiche, App's openOverview for the
  // overview pane's fiche). Omitted, or the entity type has no config.api.siblings: no arrows.
  onNavigateSibling?: (id: string | number, preview?: EntityPreview) => void;
  // True while the pane's own navigation bridge call is in flight (App.tsx's overviewBusy for the
  // overview pane) — both arrows render disabled rather than firing a jump the bean isn't ready
  // to catch up with yet, same reason the overview toolbar's own actions are withheld then.
  siblingNavDisabled?: boolean;
  // Shown in the header while this entity loads (see EntityPreview). Ignored once it has loaded,
  // and whenever it doesn't belong to the entity being shown.
  preview?: EntityPreview;
}

/**
 * Generic entity detail — tabs come entirely from config.detail.tabs (plan §3/§4). Project's
 * config registers exactly one tab (the fiche) in phase 4/6; other entities can register more
 * without any change here.
 *
 * The whole thing is one PrimeReact <Panel> (plan §7/§8 follow-up: "the toolbar is part of the
 * panel header, panels should be React panels themselves" / "reproduce the JSF header: title +
 * toolbar"). Its `header` is a single PanelHeaderBar — the status button, then
 * config.detail.header?.(entity, helpers) (the identifier chip as title, then the other chips);
 * the toolbar (prev/next inside its navigation group) sits on the right in the main pane and
 * first in the overview pane — one node, not split across <Panel>'s header/icons props.
 */
function asRecord(value: unknown): Record<string, unknown> {
  return value != null && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

// The pieces of state a tab kept, out of the fiche's saved state ({ tab, tabs: { <tabKey>: { <name>: … } } }).
function tabPieces(state: Record<string, unknown> | undefined, tabKey: string): Record<string, unknown> {
  return asRecord(asRecord(state?.tabs)[tabKey]);
}

export function EntityDetailPanel({
  entityType,
  entityId,
  toolbar,
  onNavigate,
  organizationId,
  onOpenOverview,
  overviewEntityId,
  onNavigateSibling,
  siblingNavDisabled,
  preview,
}: EntityDetailPanelProps) {
  const config = getEntityType(entityType);

  // Mirrors AbstractSingleEntityPanel.activeTabIndex — kept as this component's OWN state,
  // deliberately not TabView's own uncontrolled internal index. A sibling jump changes `entityId`,
  // which changes the detail query's key and makes this component render its loading skeleton
  // for a tick; that skeleton doesn't mount a <TabView> at all, so an uncontrolled
  // index would silently reset to 0 on the entity that follows the loading tick. This component
  // itself never unmounts across that tick (same type, same position in the tree), so this state
  // survives it — the fiche stays on whichever tab you were looking at, same as JSF's `?tab=N`.
  const [activeTabIndex, setActiveTabIndex] = usePaneState("activeTab", 0);
  // ...but ONLY within the same entity type: retargeting the overview to a DIFFERENT entity type
  // (a relationTab row click — "recording units" inside a project's fiche — opening a recording
  // unit in the overview in its place) can change how many tabs even exist, so an index carried
  // over from the previous type could point past the new one's last tab and render nothing.
  // "Adjust state during render" (React's own pattern for this, not a useEffect — that would
  // commit the stale index for one paint before correcting it): comparing against the last seen
  // entityType and resetting synchronously, within the same render, whenever it changes.
  const [renderedForType, setRenderedForType] = useState(entityType);
  if (entityType !== renderedForType) {
    setRenderedForType(entityType);
    setActiveTabIndex(0);
  }
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: queryKeys.entityDetail(entityType, entityId),
    queryFn: () => config!.api.get(entityId),
    enabled: config != null,
  });

  // The tabs of this fiche: the code's, as its project arranged them (hidden, renamed, reordered, list tabs
  // added). Known once the entity is, since the configuration is that of its project and sub-type.
  const ficheConfig = useFicheConfig(entityType, data, organizationId);
  const tabs = useMemo(
    () => (config ? resolveTabs(config.detail.tabs as DetailTabDef<{ id: string | number }>[], ficheConfig.tabs, entityType) : []),
    [config, ficheConfig.tabs, entityType],
  );

  // The tabs editor, and the list the user is looking at that they keep as a tab (« Enregistrer comme onglet… »).
  const notify = useNotify();
  useToastOnChange(error ? (error as Error).message : null);
  const [tabsEditorOpen, setTabsEditorOpen] = useState(false);
  // `{}` is a new list tab started from scratch; with a target and filters, the list the user is looking at.
  const [tabDraft, setTabDraft] = useState<{ target?: string; filters?: Record<string, FilterValue> } | null>(null);
  const addMenu = useRef<Menu>(null);
  const ficheScope = ficheScopeOf(entityType, data);
  const tabsSave = useFicheTabsSave(entityType, organizationId ?? 0, ficheScope);
  // A preview naming a tab (the relation graph opens a unit on its own graph) opens the fiche on it —
  // each time a preview arrives, not only on the first mount; same render-time adjustment.
  const [appliedPreview, setAppliedPreview] = useState<EntityPreview | undefined>(undefined);
  if (ficheConfig.settled && preview !== appliedPreview) {
    setAppliedPreview(preview);
    const tabIndex = preview?.tab ? tabs.findIndex((tab) => tab.key === preview.tab) : -1;
    if (tabIndex >= 0) setActiveTabIndex(tabIndex);
  }

  // The tab the user was on last time they opened this very fiche, kept server-side per fiche (one
  // fiche's arrangement says nothing about another's). Applied once per fiche, unless a preview names
  // its own tab; the fiche waits for it rather than opening on the first tab and jumping.
  const ficheKey = `${entityType}:${entityId}`;
  const tabState = useUserViewState(`detail:${ficheKey}`, organizationId);
  const [tabAppliedFor, setTabAppliedFor] = usePaneState<string | undefined>("detailTabAppliedFor", undefined);
  if (tabAppliedFor !== ficheKey && tabState.settled && ficheConfig.settled) {
    setTabAppliedFor(ficheKey);
    const savedTab = tabState.saved?.tab;
    const savedIndex = typeof savedTab === "string" ? tabs.findIndex((tab) => tab.key === savedTab) : -1;
    if (savedIndex >= 0 && !preview?.tab) setActiveTabIndex(savedIndex);
  }
  const tabPending = tabAppliedFor !== ficheKey && !error;
  // What a tab keeps for the user — the folded sections of its form, the mode of its relations… —
  // lives in the same state as the active tab, under the tab's own key: a tab only writes its own pieces.
  const persistedTabState = (tabKey: string) => ({
    read: (name: string) => tabPieces(tabState.saved, tabKey)[name],
    write: (name: string, value: unknown) =>
      tabState.update((s) => ({
        ...s,
        v: 1,
        tabs: { ...asRecord(s.tabs), [tabKey]: { ...tabPieces(s, tabKey), [name]: value } },
      })),
  });

  // Separate from the detail query so a slow/failed siblings lookup never blocks the fiche itself
  // from rendering — the arrows just stay disabled (SiblingNav treats `undefined` as "not ready
  // yet") until this resolves. Only ever issued when the entity type registers `api.siblings`.
  // Opened from a list, the arrows walk that list (sort, filters, search, scope); otherwise, or when
  // that list no longer holds this entity, they use the entity's default order.
  const listContext = recallListContext(entityType, entityId);
  const { data: siblings } = useQuery({
    ...siblingsQuery(config!, entityType, entityId, organizationId, listContext),
    enabled: config?.api.siblings != null,
  });

  // The fil d'Ariane (project → parents → fiche), apart from the detail for the same reason: a slow
  // lookup leaves the home + « Tous les … » crumbs in place instead of blocking the fiche.
  // Opened from a tab of another fiche (a scoped list), the trail goes through that fiche first.
  const scope = listContext?.params.scope;
  const via = scope ? { entityType: scope.entityType, id: scope.id } : undefined;
  const { data: breadcrumbs } = useQuery({
    queryKey: queryKeys.entityBreadcrumbs(entityType, entityId, via ? `${via.entityType}:${via.id}` : undefined),
    queryFn: () => config!.api.breadcrumbs!(entityId, via),
    enabled: config?.api.breadcrumbs != null,
  });

  // The titlebar's entity actions (JSF: create/duplicate/settings) — computed from this entity's
  // own data, so they are right for whichever entity is displayed, however it got here. Hooks
  // stay above the early returns below.
  const canEdit = useCanEdit(data as { _permissions?: { canEdit?: boolean } } | undefined);
  const writeMode = useWriteMode();
  const queryClient = useQueryClient();
  // Hovering or focusing a prev/next arrow warms that fiche (and its own neighbours) in the query
  // cache, so the jump usually lands on data that is already there — no skeleton at all.
  const prefetchSibling = useCallback(
    (sibling: EntitySibling) => {
      if (!config) return;
      void queryClient.prefetchQuery({
        queryKey: queryKeys.entityDetail(entityType, sibling.id),
        queryFn: () => config.api.get(sibling.id),
      });
      if (config.api.siblings) {
        // The context the sibling will have once opened (see the arrow's click), so this lands on
        // the entry its own render asks for.
        const context = neighbourContext(listContext, sibling);
        void queryClient.prefetchQuery(siblingsQuery(config, entityType, sibling.id, organizationId, context));
      }
    },
    [config, entityType, organizationId, queryClient, listContext],
  );
  // Where the creation overlay opens (null = closed): the clicked "Créer" button, or the panel's
  // own toolbar when it was picked from the "…" menu, which is gone by the time the overlay opens.
  const [createAnchor, setCreateAnchor] = useState<HTMLElement | null>(null);
  const panelRef = useRef<Panel>(null);
  // The arrows' own jump, shared with the swipe: the neighbour carries on walking the same list, from its own position.
  const goToSibling = (sibling: EntitySibling) => {
    const context = neighbourContext(listContext, sibling);
    if (context) rememberListContext(entityType, sibling.id, context);
    onNavigateSibling?.(sibling.id, { label: sibling.label });
  };
  useSwipeSiblings(() => panelRef.current?.getElement(), {
    previous: siblings?.previous,
    next: siblings?.next,
    go: goToSibling,
    enabled: config?.api.siblings != null && onNavigateSibling != null && !siblingNavDisabled,
  });
  function openCreate(event?: SyntheticEvent) {
    const clicked = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    const toolbarEl = panelRef.current?.getElement()?.querySelector<HTMLElement>(".panel-toolbar") ?? null;
    setCreateAnchor(clicked && clicked.isConnected && !clicked.closest(".p-menu") ? clicked : toolbarEl);
  }
  const duplicateMutation = useMutation({
    mutationFn: () => config!.api.duplicate!(entityId),
    onSuccess: (copy) => {
      // The copy shows up in the lists of its type only.
      void queryClient.invalidateQueries({ queryKey: queryKeys.entityList(entityType) });
      // JSF opens the copy in the overview (FlowBean.addRecordingUnitToOverview).
      (onOpenOverview ?? onNavigate)?.(entityType, copy.id);
    },
  });

  const deletion = useDeleteEntity(entityType, config, organizationId);

  // The main pane's fiche names the browser tab after its entity (focus.xhtml's <title> only knows
  // the entity JSF rendered). The overview's fiche doesn't.
  const documentTitle =
    toolbar && !toolbar.actions?.closeOverview && data && config?.detail.chrome ? config.detail.chrome(data).title : "";
  useEffect(() => {
    if (documentTitle) document.title = documentTitle;
  }, [documentTitle]);

  if (!config) {
    return <div className="entity-detail-panel-unsupported">Unknown entity type &quot;{entityType}&quot;</div>;
  }
  const siblingNav =
    config.api.siblings && onNavigateSibling ? (
      <SiblingNav
        siblings={siblings}
        onNavigate={goToSibling}
        onPrefetch={prefetchSibling}
        disabled={siblingNavDisabled}
      />
    ) : undefined;
  const headerLayout = toolbar?.actions?.closeOverview ? "overview" : "main";

  if (isLoading || tabPending) {
    // Same frame as the loaded fiche — panel, header, toolbar — so nothing jumps when the data
    // arrives: the identifier from the preview (or a skeleton), the rest as skeletons.
    return (
      <Panel
        className="entity-detail-panel entity-detail-panel-loading"
        header={
          <PanelHeaderBar
            layout={headerLayout}
            navigation={siblingNav}
            title={
              <span className="entity-detail-panel-preview-header">
                <Skeleton shape="circle" size="2rem" />
                {preview ? (
                  // The header's own identifier chip class (<entity>-chip-alt, filled in the
                  // entity's colour by the theme), so nothing changes colour when the entity loads.
                  <span
                    className={`p-chip p-component entity-nav-chip entity-detail-panel-preview-chip ${config.panelClass ? config.panelClass.replace(/-panel$/, "-chip-alt") : ""}`}
                  >
                    <i className={`p-chip-icon ${config.icon}`} aria-hidden="true" />
                    <span className="p-chip-text">{preview.label}</span>
                  </span>
                ) : (
                  <Skeleton width="8rem" height="2rem" borderRadius="16px" />
                )}
                <Skeleton width="6rem" height="2rem" borderRadius="16px" className="loading-skeleton" />
              </span>
            }
            toolbar={toolbar}
          />
        }
      >
        <DetailBodySkeleton />
      </Panel>
    );
  }
  if (error) {
    return null;
  }
  if (!data) {
    return null;
  }

  // After a save from the fiche or its header: this detail, and the rows showing this entity in the
  // lists of its type (a list in the other pane shows the new value without a reload).
  const afterSave = () => {
    void refetch();
    void queryClient.invalidateQueries({ queryKey: queryKeys.entityList(entityType) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.entityBreadcrumbs(entityType, entityId) });
  };
  const helpers = { refetch: afterSave, organizationId, onNavigate, onOpenOverview, overviewEntityId };

  // A toolbar built server-side (MountOptions) has no entity data to derive chrome from at that
  // point — it's the initial mount's own entity. Once we have the fetched entity, prefer chrome
  // freshly derived from it via config.detail.chrome when the entity type registers one: this is
  // what makes a client-opened overview's toolbar (plan §8 phase 5, EntityListPanel's
  // onOpenOverview — no server round-trip to build a toolbar from) possible at all, and it also
  // keeps title/bookmark state current after an in-place edit for the originally-seeded overview.
  const settingsProjectId = config.detail.settingsProjectId?.(data);
  // Every other fiche of the project (not the project itself, not a place) leads to the configuration of its
  // type, and of its fields, for whoever may manage the project's settings.
  const settingsTable = settingsTableOf(entityType);
  const configurable = data as { projectId?: string | number | null; type?: { id?: string | number } | null; _permissions?: { canManageSettings?: boolean } };
  const configureProjectId =
    settingsTable != null && configurable._permissions?.canManageSettings === true ? configurable.projectId ?? undefined : undefined;
  const configureTypeId = configurable.type?.id;
  // Whoever may edit the default views of this project arranges its fiches' tabs, in edit mode like the other changes.
  const canConfigureTabs = ficheConfig.canEdit && writeMode && organizationId != null;
  // The project's configuration with `rows`, saved at once, and the fiche opened on the tab `openKey`.
  const saveRows = async (rows: EditableTab[], openKey: string) => {
    const subTypeOnly = ficheScope.typeId != null;
    try {
      await tabsSave.save(rows, subTypeOnly);
      setTabDraft(null);
      notify.success(t("tabs.saved"));
      setActiveTabIndex(Math.max(0, rows.filter((row) => !row.hidden).findIndex((row) => row.key === openKey)));
    } catch (failure) {
      notify.error(messageForError(failure, t("tabs.failed")));
    }
  };
  const currentRows = () => editableTabs(config.detail.tabs as DetailTabDef<never>[], ficheConfig.tabs);
  const addDraftTab = (tab: EditableTab) => saveRows([...currentRows(), tab], tab.key);
  const restoreBuiltIn = (key: string) => saveRows(restoreTab(currentRows(), key), key);
  const listTargets = relationTargets(config.detail.tabs as DetailTabDef<never>[]);
  const restorable = restorableTabs(currentRows());
  const addItems = addTabItems(restorable, listTargets.length > 0, (key) => void restoreBuiltIn(key), () => setTabDraft({}));
  // Every entity resource carries its TraceableEntity status and the validator right.
  const validation = data as { validationStatus?: string | null; _permissions?: { canValidate?: boolean } };
  const entityActions: PanelActions = {
    create: canEdit && config.list.createForm ? openCreate : undefined,
    duplicate:
      canEdit && config.api.duplicate && !duplicateMutation.isPending ? () => duplicateMutation.mutate() : undefined,
    settings:
      settingsProjectId != null
        ? () => openProjectSettings(settingsProjectId)
        : configureProjectId != null && settingsTable != null
          ? () => openTablesSettings(configureProjectId, settingsTable, configureTypeId)
          : undefined,
    delete:
      writeMode && deletion.canDelete && (data as { _permissions?: { canDelete?: boolean } })._permissions?.canDelete === true
        ? () =>
            deletion.requestDelete({
              id: entityId,
              name: String((data as { name?: unknown }).name ?? config.detail.chrome?.(data).title ?? entityId),
            })
        : undefined,
  };
  const resolvedToolbar: PanelToolbarSlot | undefined = toolbar && {
    ...toolbar,
    chrome: config.detail.chrome?.(data) ?? toolbar.chrome,
    actions: { ...toolbar.actions, ...entityActions },
  };

  return (
    <Panel
      ref={panelRef}
      className="entity-detail-panel"
      header={
        <PanelHeaderBar
          layout={headerLayout}
          navigation={siblingNav}
          title={
            <>
              {/* validationButton.xhtml's place in the JSF headers, now on every fiche that has a status. */}
              {validation.validationStatus !== undefined && (
                <ValidationStatusButton
                  entityType={entityType}
                  collectionPath={config.collectionPath}
                  entityId={entityId}
                  status={validation.validationStatus}
                  canEdit={canEdit}
                  canValidate={writeMode && validation._permissions?.canValidate === true}
                />
              )}
              {/* The identifier chip (the fiche's title), then the entity's other chips. */}
              {config.detail.header?.(data, helpers)}
            </>
          }
          toolbar={resolvedToolbar}
        />
      }
    >
      {/* singleUnitPanel.xhtml's own p:breadCrumb, above the tabs and inside the panel body.
          AbstractSingleEntityPanel.getAllParentBreadcrumbModels builds exactly two items for a
          non-hierarchical entity like Project — the home item and the "root type" item — both of
          which the registry already knows, so this needs no endpoint. An entity whose panel
          overrides that method with real parents (Specimen, Phase, Container) will need its own
          crumbs from config; it gets this two-item base until then. */}
      <EntityBreadcrumbTrail
        // createHomeItem: icon only, no label, and a real redirect to the dashboard
        // (FlowBean.redirectToDashboard → /focus/L3dlbGNvbWU=, the base64url of "/welcome"). Not
        // routed through onNavigate: there is no "home" entity type for the registry to resolve,
        // and the dashboard is a JSF page, not one of the three React panel kinds.
        home={{ icon: "bi bi-house", url: apiUrl("/focus/L3dlbGNvbWU=") }}
        fallback={[
          {
            // ActionUnitPanel.createRootTypeItem: "Tous les projets", linking to the entity's list.
            label: config.labels.all ?? t("detail.allOf", { plural: config.labels.plural.toLowerCase() }),
            icon: config.icon,
            command: onNavigate ? () => onNavigate(entityType) : undefined,
          },
        ]}
        breadcrumbs={breadcrumbs}
        onNavigate={onNavigate}
      />
      <CreateEntityOverlay
        entityType={entityType}
        anchor={createAnchor}
        organizationId={organizationId}
        scope={config.detail.createScope?.(data)}
        onHide={() => setCreateAnchor(null)}
        onCreated={(id) => {
          setCreateAnchor(null);
          void queryClient.invalidateQueries({ queryKey: queryKeys.entityList(entityType) });
          // Same destination as the list toolbar's create: the overview, so the current view stays
          // in place (the full fiche is one click away, the overview's focus button).
          (onOpenOverview ?? onNavigate)?.(entityType, id);
        }}
      />
      {deletion.dialog}
      {tabsEditorOpen && canConfigureTabs && (
        <FicheTabsEditor
          entityType={entityType}
          entityId={entityId}
          organizationId={organizationId}
          scope={ficheScope}
          codeTabs={config.detail.tabs as DetailTabDef<never>[]}
          saved={ficheConfig.tabs}
          savedTypeId={ficheConfig.typeId}
          onHide={() => setTabsEditorOpen(false)}
          onChanged={() => setActiveTabIndex(0)}
        />
      )}
      {tabDraft && canConfigureTabs && (
        <AddListTabDialog
          entityType={entityType}
          entityId={entityId}
          projectId={ficheScope.projectId}
          organizationId={organizationId}
          targets={relationTargets(config.detail.tabs as DetailTabDef<never>[])}
          initial={tabDraft.target && tabDraft.filters ? { target: tabDraft.target, filters: tabDraft.filters } : undefined}
          onHide={() => setTabDraft(null)}
          onAdd={(tab) => void addDraftTab(tab)}
        />
      )}
      <Menu ref={addMenu} popup model={addItems} />
      <FieldConfigProvider
        value={
          configureProjectId != null && settingsTable != null && configureTypeId != null
            ? { configure: (field) => openTablesSettings(configureProjectId, settingsTable, configureTypeId, field.id) }
            : undefined
        }
      >
      <FicheTabsProvider value={canConfigureTabs ? { saveAsTab: (target, filters) => setTabDraft({ target, filters }) } : undefined}>
        <TabView
          className="entity-detail-panel-tabs"
          // One line, scrolled with ‹ › when the tabs don't fit (the narrow overview pane).
          scrollable
          activeIndex={activeTabIndex}
          onTabChange={(e) => {
            // The two shortcuts after the last tab are buttons, not tabs.
            if (canConfigureTabs && addItems.length > 0 && e.index === tabs.length) {
              if (restorable.length > 0) addMenu.current?.toggle(e.originalEvent);
              else if (listTargets.length > 0) setTabDraft({});
              return;
            }
            if (canConfigureTabs && e.index === tabs.length + (addItems.length > 0 ? 1 : 0)) {
              setTabsEditorOpen(true);
              return;
            }
            setActiveTabIndex(e.index);
            const tab = tabs[e.index];
            // Merged: the state the other tabs kept stays.
            if (tab) tabState.update((s) => ({ ...s, v: 1, tab: tab.key }));
          }}
        >
          {tabs.map((tab) => {
            const badge = tab.badge?.(data, helpers);
            // Resolved here: the registry is complete by now, not when the tab was defined.
            const icon = tab.icon ?? (tab.relation ? getEntityType(tab.relation.target)?.icon : undefined);
            return (
              <TabPanel
                key={tab.key}
                header={
                  // pages/shared/tab/tabTitle.xhtml's markup, as every JSF tab renders its title:
                  // bold label, then the count in parentheses when the tab has one.
                  <div className="sia-tab-title">
                    {icon && <i className={`${icon} sia-tab-icon`} aria-hidden="true" />}
                    {tab.label}
                    {badge != null && (isValidElement(badge) ? badge : <TabCount value={String(badge)} />)}
                  </div>
                }
              >
                <PaneErrorBoundary label={t("detail.tabError", { label: tab.label })} resetKey={`${entityType}:${entityId}`}>
                  <PersistedTabState value={persistedTabState(tab.key)}>
                    <PaneStateScope name={`tab:${tab.key}`}>{tab.render(data, helpers)}</PaneStateScope>
                  </PersistedTabState>
                </PaneErrorBoundary>
              </TabPanel>
            );
          })}
          {canConfigureTabs && addItems.length > 0 && (
            <TabPanel
              key="__add"
              headerClassName="fiche-tabs-shortcut"
              header={
                // The same title markup as the tabs themselves, so both read the same.
                <div className="sia-tab-title fiche-tabs-shortcut-label" title={t("tabs.add")}>
                  <i className="bi bi-plus-lg" aria-hidden="true" /> {t("tabs.addShortcut")}
                </div>
              }
            />
          )}
          {canConfigureTabs && (
            <TabPanel
              key="__edit"
              headerClassName="fiche-tabs-shortcut"
              header={
                <div className="sia-tab-title fiche-tabs-shortcut-label" title={t("tabs.editorTitle")}>
                  <i className="bi bi-pencil" aria-hidden="true" /> {t("tabs.editShortcut")}
                </div>
              }
            />
          )}
        </TabView>
      </FicheTabsProvider>
      </FieldConfigProvider>
    </Panel>
  );
}
