import { useEffect, useMemo } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { NotifyProvider } from "./notify/NotifyProvider";
import { PaneErrorBoundary } from "./components/PaneErrorBoundary";
import { queryClient } from "./api/queryClient";
import type { MountOptions, PanelChrome, PanelKind, PanelToolbarSlot } from "./mountOptions";
import { homeShortcutWidgets } from "./components/home/HomeShortcuts";
import { getAllEntityTypes, getEntityType } from "./entities/registry";
import { EntityListPanel } from "./panels/EntityListPanel";
import { EntityDetailPanel, type EntityPreview } from "./panels/EntityDetailPanel";
import { HomePanel } from "./panels/HomePanel";
import { WriteModeProvider } from "./panels/writeMode";
import { BridgeProvider } from "./panels/bridge";
import { EntityNavigationProvider } from "./panels/entityNavigation";
import { paneTransitionName } from "./panels/panelTransition";
import { PaneSplit } from "./panels/PaneSplit";
import { PaneStateProvider, PaneStateScope, usePaneStateStore } from "./panels/paneState";
import { sameEntity, type NavigationView, type OverviewState } from "./navigation/reducer";
import { useNavigation } from "./navigation/useNavigation";
import { registerNavigation } from "./navigation/externalBus";
import { onEntityDeleted } from "./panels/entityDeleted";
import { ContextPublisher } from "./navigation/ContextPublisher";
import { PROJECT_KEY } from "./entities/keys";
import type { ListScope } from "./entities/types";
import { t } from "./i18n";

// One cache for the whole app: see api/queryClient.ts.

// The classes JSF puts on a panel's root (AbstractPanel.panelClass: "siamois-panel
// <entity>-panel single-panel|list-panel", WelcomePanel's bare "siamois-panel"). They select the
// entity's color scheme and the app's panel-scoped overrides in the shared theme — per pane, and
// per what the pane shows NOW: the JSF wrapper around the mount only knows what it mounted.
export function paneClassName(panelKind: PanelKind, entityType: string): string {
  if (panelKind === "home") return "siamois-panel";
  const panelClass = getEntityType(entityType)?.panelClass;
  const kind = panelKind === "list" ? "list-panel" : "single-panel";
  return panelClass ? `siamois-panel ${panelClass} ${kind}` : `siamois-panel ${kind}`;
}

// Routes to the one generic panel matching panelKind — no per-entity branching here either,
// that all lives inside the three panels themselves via the registry (plan §3).
function PanelContent({
  view,
  organizationId,
  onNavigate,
  onOpenOverview,
  overview,
  onCreate,
  toolbar,
  onOpenView,
}: {
  view: NavigationView;
  organizationId?: number;
  // Opens a saved view of the list in this pane (without an id, the default list).
  onOpenView?: (entityType: string, viewId?: number) => void;
  onNavigate: (entityType: string, id?: string | number, preview?: EntityPreview) => void;
  // Only meaningful for a "list" view — opens the entity in the overview pane instead of
  // navigating the main pane away from the list (plan §8 phase 5).
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  // So a "list" view can highlight the row matching the currently-open overview (plan §8 phase
  // 5's rowClassName) — only when it's the same entity type as this list, and only while an
  // overview is actually open.
  overview?: OverviewState | null;
  onCreate?: () => void;
  // Passed straight into whichever panel is actually showing — each panel (Home/List/Detail) now
  // renders this itself, as part of its own PrimeReact <Panel> header, rather than PanelContent
  // or its caller rendering a toolbar strip above the panel (plan §7/§8 follow-up: "the toolbar
  // is part of the panel header").
  toolbar?: PanelToolbarSlot;
}) {
  switch (view.panelKind) {
    case "home": {
      // Assembled from every registered entity's own `home.widgets` (plan §8 phase 7) — not a
      // switch/import per entity, same "registry, not a switch" principle List/Detail already
      // follow. Most entities won't declare `home` at all yet, so this stays empty until they do.
      const home = { organizationId, onNavigate, onOpenView, onOpenOverview };
      const widgets = [
        ...getAllEntityTypes().flatMap((config) => config.home?.widgets(home) ?? []),
        ...homeShortcutWidgets(home),
      ];
      return <HomePanel widgets={widgets} toolbar={toolbar} />;
    }
    case "list":
      return (
        <EntityListPanel
          entityType={view.entityType}
          organizationId={organizationId}
          onNavigate={onNavigate}
          onOpenOverview={onOpenOverview}
          overviewEntityId={overview?.entityType === view.entityType ? overview.entityId : undefined}
          onCreate={onCreate}
          toolbar={toolbar}
          viewId={view.viewId}
          onViewChange={onOpenView && ((viewId) => onOpenView(view.entityType, viewId))}
        />
      );
    case "detail":
      if (view.entityId == null) {
        return <div className="entity-detail-panel-error">Missing entityId for detail panel</div>;
      }
      return (
        <EntityDetailPanel
          entityType={view.entityType}
          entityId={view.entityId}
          preview={view.preview}
          toolbar={toolbar}
          onNavigate={onNavigate}
          organizationId={organizationId}
          onOpenOverview={onOpenOverview}
          overviewEntityId={overview?.entityType === view.entityType ? overview.entityId : undefined}
          // A sibling jump on the MAIN pane's own fiche is a same-pane navigation — the same
          // `navigate` a list row click or the breadcrumb already use, just staying on "detail".
          onNavigateSibling={(id, preview) => onNavigate(view.entityType, id, preview)}
        />
      );
  }
}

/**
 * Root component for one mounted panel instance. This sits ABOVE both panes — it's the direct
 * replacement for panelContent.xhtml's entire body (plan §3: "React owns the whole splitter,
 * not just the main pane"), not just the main pane's inner content. One mount() call per
 * panelContent.xhtml instance; focus.xhtml never mounts the two panes separately.
 *
 * - No overview open (options.overviewEntityType unset): renders only the main pane, no
 *   splitter/gutter — mirrors JSF's own p:splitter today, which has a single splitterPanel
 *   when panelModel.parentOrOverview is null.
 * - Overview open: PaneSplit (panels/PaneSplit.tsx) is the equivalent of JSF's
 *   p:splitter/p:splitterPanel, built so the main pane is never remounted when the overview
 *   opens or closes — left pane = the main panel's entity, right pane =
 *   parentOrOverview's entity. Class names (panel-splitter-panel-l/-r, sideview,
 *   sideview-titlebar) mirror the JSF markup 1:1 per the class-name-preservation rule (§3) —
 *   audit the real markup before extending this, don't rely on this comment as the full list.
 *   The overview pane is always a "detail"-kind render (there's no list/home overview in JSF
 *   today) — it does not go through PanelContent's panelKind switch above.
 *
 * Both panes get their own titlebar (plan §7.3/§8 phase 8), matching JSF's own two separate
 * toolbars (focus.xhtml's for the main panel, panelContent.xhtml's for the overview) — but that
 * titlebar is no longer rendered here as a strip above the panel. Each panel (HomePanel/
 * EntityListPanel/EntityDetailPanel) is itself a PrimeReact <Panel> and renders its own toolbar
 * (via the shared PanelToolbar component) in its `icons`, right next to its own header content —
 * matching the real markup's single sideview-titlebar div that holds both side by side. App only
 * builds the `PanelToolbarSlot` (chrome/organizationId/actions) and hands it down.
 *
 * The main pane's *content* is a client-side router, not a sequence of page loads (the whole
 * point of this migration is to stop paying for a JSF round-trip on every navigation — plan §3's
 * "no router library" was written before every panelKind existed in React and meant "don't
 * reimplement FlowBean's server-owned open/close", not "reload the page to switch panels"). A
 * click inside the mounted tree (a list row, a Home widget link) calls `navigate` below, which
 * swaps `view` and updates the URL via history.pushState — no navigation, no remount, no JSF.
 *
 * Toolbars never depend on what JSF mounted: a detail panel builds its own actions (create,
 * duplicate, settings) and bookmark chrome from its entity's REST data, and App only adds the
 * pane-level ones (closeFocus, closeOverview, fullscreen). So both toolbars stay valid, and
 * visible, whatever the panes navigate to. `options.main` is only the chrome of the view JSF
 * mounted with, used until the main pane leaves it.
 */
export function App({ options }: { options: MountOptions }) {
  // Outlives the panes: focus mode moves a fiche between the two panes (a remount), and each
  // panel restores its tab, filters and folded sections from here.
  const paneStateStore = usePaneStateStore();
  const { state, navigate, openView, openOverview, openList, closeOverview, enterFocus, closeFocus } = useNavigation(options);
  const { view, overview, navigatedAway } = state;

  // What the top search bar (its own React root) can drive.
  const external = useMemo(() => ({ navigate, openOverview, openList }), [navigate, openOverview, openList]);
  useEffect(() => registerNavigation(external), [external]);

  // A deleted entity can no longer be shown: close the overview that displays it, and leave a main
  // pane showing its fiche for the list of its type.
  useEffect(
    () =>
      onEntityDeleted((entityType, id) => {
        if (overview?.entityType === entityType && overview.entityId != null && String(overview.entityId) === String(id)) {
          closeOverview();
        }
        if (view.panelKind === "detail" && view.entityType === entityType && view.entityId != null && String(view.entityId) === String(id)) {
          navigate(entityType);
        }
      }),
    [overview, view, closeOverview, navigate],
  );

  // options.overview is the chrome of the overview JSF mounted with — only valid for that entity.
  // For anything else it's a placeholder until EntityDetailPanel derives the real one from
  // config.detail.chrome once its data loads.
  const overviewChromeFor = (entity: OverviewState): PanelChrome =>
    entity.entityId == null
      ? { resourceUri: getEntityType(entity.entityType)?.routes.list ?? "", title: getEntityType(entity.entityType)?.labels.plural ?? "" }
      : options.overview &&
    sameEntity(entity, options.overviewEntityType != null && options.overviewEntityId != null
      ? { entityType: options.overviewEntityType, entityId: options.overviewEntityId }
      : null)
      ? options.overview
      : { resourceUri: "", title: "", bookmarked: false };

  // The main pane's placeholder chrome (a detail panel replaces it with its entity's own): the
  // server-built one while still on the view JSF mounted, otherwise derived from the registry —
  // bookmark state unknown, which PanelToolbar then fetches itself.
  const viewConfig = getEntityType(view.entityType);
  const mainChrome: PanelChrome = !navigatedAway
    ? options.main
    : view.panelKind === "list" && viewConfig
      ? { resourceUri: viewConfig.routes.list, title: viewConfig.labels.plural }
      : { resourceUri: "", title: "" };

  // The browser tab's title follows the main pane: focus.xhtml's <title> is only right for the view
  // JSF rendered. A fiche sets its own once its entity loads (EntityDetailPanel).
  const mainTitle = navigatedAway && view.panelKind !== "detail" ? mainChrome.title : "";
  useEffect(() => {
    if (mainTitle) document.title = mainTitle;
  }, [mainTitle]);

  const inFocus = state.focusStack.length > 0;
  const mainToolbar: PanelToolbarSlot = {
    chrome: mainChrome,
    organizationId: inFocus ? (options.overviewOrganizationId ?? options.organizationId) : options.organizationId,
    // Back out of focus mode: the client-side stack, or — page loaded in focus mode — goBackUrl.
    actions: state.backUrl != null ? { closeFocus } : undefined,
  };

  const overviewToolbar: PanelToolbarSlot | undefined = overview
    ? {
        chrome: overviewChromeFor(overview),
        organizationId: options.overviewOrganizationId ?? options.organizationId,
        // closeOverview is App's own wrapper (it also clears the React overview state and the
        // URL); fullscreen is App's client-side focus swap (enterFocus).
        // A list has no fiche to promote to the main pane.
        actions: overview.entityId == null ? { closeOverview } : { closeOverview, fullscreen: enterFocus },
      }
    : undefined;

  // Named per shown entity (see paneTransitionName). The overview gets a suffix only in the odd
  // case both panes show the very same entity — names must be unique on the page.
  const mainPaneName = paneTransitionName(view.panelKind, view.entityType, view.entityId);
  const overviewKind = overview?.entityId == null ? "list" : "detail";
  const overviewPaneName = overview
    ? paneTransitionName(overviewKind, overview.entityType, overview.entityId)
    : undefined;
  const overviewPaneStyleName = overviewPaneName === mainPaneName ? `${overviewPaneName}-overview` : overviewPaneName;

  // Keyed on the kind and type shown, not the entity id: a sibling jump or a same-type retarget
  // keeps the panel mounted (its active tab survives, as in JSF's `?tab=`), while switching to a
  // different kind or type starts fresh. Entity-bound local state resets itself on the entity
  // (PanelToolbar's bookmark flag follows chrome.resourceUri).
  // Only the panes that can still come back keep their state: the two on screen and those stacked
  // by focus mode (the main views left behind, and the overviews promoted over them). After the
  // panels' own effects (children run first), so a value carried to a new identity is written first.
  const stackScopes = state.focusStack.flatMap((level) => [
    paneTransitionName(level.view.panelKind, level.view.entityType, level.view.entityId),
    paneTransitionName("detail", level.overview.entityType, level.overview.entityId),
  ]);
  const liveScopes = [mainPaneName, ...(overviewPaneStyleName ? [overviewPaneStyleName] : []), ...stackScopes].join("\n");
  useEffect(() => {
    paneStateStore.retain(liveScopes.split("\n"));
  }, [paneStateStore, liveScopes]);

  const mainPane = (
    <PaneErrorBoundary label={t("pane.main")} resetKey={`${view.panelKind}:${view.entityType}:${view.entityId ?? ""}`}>
    <PaneStateScope name={mainPaneName}>
    <PanelContent
      key={`${view.panelKind}:${view.entityType}`}
      view={view}
      organizationId={options.organizationId}
      onNavigate={navigate}
      onOpenView={openView}
      onOpenOverview={openOverview}
      overview={overview}
      toolbar={mainToolbar}
    />
    </PaneStateScope>
    </PaneErrorBoundary>
  );

  const overviewPane = overview && (
    <PaneErrorBoundary label={t("pane.overview")} resetKey={`${overview.entityType}:${overview.entityId ?? ""}`}>
    <PaneStateScope name={overviewPaneStyleName ?? ""}>
    {overview.entityId == null ? (
      <EntityListPanel
        entityType={overview.entityType}
        organizationId={options.overviewOrganizationId ?? options.organizationId}
        scope={seedScope(overview)}
        seed={overview.seed}
        onNavigate={(entityType, id) => openOverview(entityType, id)}
        onOpenOverview={openOverview}
        toolbar={overviewToolbar}
      />
    ) : (
    <EntityDetailPanel
      entityType={overview.entityType}
      entityId={overview.entityId}
      preview={overview.preview}
      toolbar={overviewToolbar}
      organizationId={options.overviewOrganizationId ?? options.organizationId}
      // A click inside the overview pane's own fiche (e.g. a row in a relationTab, like a
      // project's UE list) retargets the OVERVIEW pane, not the main one.
      onOpenOverview={openOverview}
      // A crumb of the overview's fil d'Ariane retargets the overview too: a fiche goes to the
      // overview pane, a list (no id) to the main pane.
      onNavigate={(entityType, id) => (id != null ? openOverview(entityType, id) : navigate(entityType))}
      // Deliberately NOT overviewEntityId={overview.entityId}: a relationTab embedded here renders
      // a DIFFERENT entity type than the overview's own, and EntityListPanel's row highlighting
      // compares raw ids with no entityType guard.
      // A sibling jump on the OVERVIEW pane's own fiche retargets the overview in place — never
      // `navigate`, which would move the MAIN pane instead.
      onNavigateSibling={(id, preview) => openOverview(overview.entityType, id, preview)}
    />
    )}
    </PaneStateScope>
    </PaneErrorBoundary>
  );

  // One stable tree whether or not an overview is open (see PaneSplit): the main pane is never
  // remounted by opening or closing the overview. The main pane carries the legacy panel-docked
  // box's colored top border (focus.xhtml); .sideview already gives the overview its own.
  const content = (
    <PaneSplit
      main={mainPane}
      mainClassName={`panel-splitter-panel-l ${paneClassName(view.panelKind, view.entityType)}`}
      mainStyle={{ borderTop: "3px solid var(--main-color)", viewTransitionName: mainPaneName }}
      overview={overviewPane || null}
      overviewClassName={`panel-splitter-panel-r sideview ${overview ? paneClassName(overviewKind, overview.entityType) : ""}`}
      overviewStyle={{ viewTransitionName: overviewPaneStyleName }}
    />
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ContextPublisher view={view} organizationId={options.organizationId} />
      <NotifyProvider>
      {/* FlowBean.isWriteMode, for every panel below — see panels/writeMode.tsx for why this is a
          context and why it needs no change subscription. */}
      <WriteModeProvider value={options.writeMode === true}>
        <BridgeProvider value={options.bridge}>
          <EntityNavigationProvider value={openOverview}>
            <PaneStateProvider store={paneStateStore}>{content}</PaneStateProvider>
          </EntityNavigationProvider>
        </BridgeProvider>
      </WriteModeProvider>
      </NotifyProvider>
    </QueryClientProvider>
  );
}

// The sub-collection of a project a list opened with a project filter is read from: only the kinds
// the API serves under /projects/{id}/… (the others are searched organization-wide).
const PROJECT_SCOPED_LISTS = new Set(["recordingUnit", "find"]);

function seedScope(overview: OverviewState): ListScope | undefined {
  const projectId = overview.seed?.projectId;
  if (projectId == null || !PROJECT_SCOPED_LISTS.has(overview.entityType)) return undefined;
  return { entityType: PROJECT_KEY, id: projectId };
}
