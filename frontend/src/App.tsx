import { useCallback, useEffect, useRef, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Splitter, SplitterPanel } from "primereact/splitter";
import type { MountOptions, PanelChrome, PanelKind, PanelToolbarSlot } from "./mountOptions";
import { apiUrl } from "./api/basePath";
import { getAllEntityTypes, getEntityType } from "./entities/registry";
import { EntityListPanel } from "./panels/EntityListPanel";
import { EntityDetailPanel } from "./panels/EntityDetailPanel";
import { HomePanel } from "./panels/HomePanel";
import { WriteModeProvider } from "./panels/writeMode";
import { BridgeProvider } from "./panels/bridge";

const queryClient = new QueryClient();

// What's actually on screen in the main pane right now — starts from the mount options JSF gave
// us, but from then on this is owned entirely client-side (see App below). Deliberately NOT the
// same object as MountOptions: this is the one part of it that changes after mount.
interface NavigationState {
  panelKind: PanelKind;
  entityType: string;
  entityId?: string | number;
}

// What's open in the right-hand overview pane right now (plan §8 phase 5) — starts from the
// mount options (a page load/F5 always has an authoritative server-rendered overview, if any),
// but from openOverview onward this is owned entirely client-side, same as `view` above.
interface OverviewState {
  entityType: string;
  entityId: string | number;
}

// One level of focus mode: the overview entity was promoted to the main pane, and this is what
// closeFocus restores. A stack of these, not a single slot — focusing again from within focus mode
// just pushes another level, the same way JSF's own `back=` param nests (FlowBean.fullScreen encodes
// the complete previous focus URL, which carries its own `back=`).
interface FocusSnapshot {
  view: NavigationState;
  mainPath: string;
  // The entity that was promoted — it goes back into the overview pane on closeFocus.
  overview: OverviewState;
  // Restored as-is, so the original server-built main toolbar comes back only if it was still
  // valid when focus mode was entered.
  navigatedAway: boolean;
  // The `back=` of the state being left, so a nested closeFocus puts the right URL back.
  backUrl?: string;
}

function sameEntity(a: OverviewState | null | undefined, b: OverviewState | null | undefined): boolean {
  return a != null && b != null && a.entityType === b.entityType && String(a.entityId) === String(b.entityId);
}

// Base64url, no padding — exactly Java's Base64.getUrlEncoder().withoutPadding(), which is what
// FlowBean.redirectToFocus/showSideview and FocusViewBean's own decode already use for the
// `/focus/<main>?s=<overview>` URL scheme (template.js's showSideview builds the same URL for
// JSF's own row click). Entity resourceUris here are always plain ASCII ("action-unit/123"), so
// no unicode handling is needed beyond what encodeURIComponent/unescape already give us.
function base64url(value: string): string {
  return btoa(unescape(encodeURIComponent(value)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

// Every URL this app pushes must be the canonical `/focus/<main>[?s=<overview>]` form that
// FocusViewBean decodes for every entity type — not a bare entity route. The address bar is what
// an F5 replays, and a bare route either has no Spring controller at all or lands on a template
// without the React branch; the main token must also be the main pane's CURRENT view, not the
// one JSF originally mounted, or an F5 silently brings back the page the user already left.
// `backUrl` is focus mode's way back (FlowBean.redirectToFocus's own `back=` param, which
// FocusViewBean decodes into AbstractPanel.goBackUrl) — an absolute, context-path-prefixed URL, same
// as the ones this function returns, so a focus URL can nest inside another's `back=`.
function focusUrl(mainPath: string, overviewPath?: string, backUrl?: string): string {
  const params: string[] = [];
  if (overviewPath) params.push(`s=${base64url(overviewPath)}`);
  if (backUrl) params.push(`back=${base64url(backUrl)}`);
  const main = apiUrl(`/focus/${base64url(mainPath)}`);
  return params.length ? `${main}?${params.join("&")}` : main;
}

function overviewPath(overview: OverviewState | null): string | undefined {
  if (!overview) return undefined;
  return getEntityType(overview.entityType)?.routes.detail(overview.entityId);
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
}: {
  view: NavigationState;
  organizationId?: number;
  onNavigate: (entityType: string, id?: string | number) => void;
  // Only meaningful for a "list" view — opens the entity in the overview pane instead of
  // navigating the main pane away from the list (plan §8 phase 5).
  onOpenOverview?: (entityType: string, id: string | number) => void;
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
      const widgets = getAllEntityTypes().flatMap(
        (config) => config.home?.widgets({ organizationId, onNavigate }) ?? [],
      );
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
          toolbar={toolbar}
          onNavigate={onNavigate}
          organizationId={organizationId}
          onOpenOverview={onOpenOverview}
          overviewEntityId={overview?.entityType === view.entityType ? overview.entityId : undefined}
          // A sibling jump on the MAIN pane's own fiche is a same-pane navigation — the same
          // `navigate` a list row click or the breadcrumb already use, just staying on "detail".
          onNavigateSibling={(id) => onNavigate(view.entityType, id)}
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
 * - Overview open: PrimeReact's Splitter/SplitterPanel is the direct equivalent of JSF's
 *   p:splitter/p:splitterPanel — left pane = the main panel's entity, right pane =
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
  const [view, setView] = useState<NavigationState>({
    panelKind: options.panelKind,
    entityType: options.entityType,
    entityId: options.entityId,
  });
  const [navigatedAway, setNavigatedAway] = useState(false);
  // Read by enterFocus to snapshot the state being left, without re-creating the callback.
  const viewRef = useRef(view);
  viewRef.current = view;
  const navigatedAwayRef = useRef(navigatedAway);
  navigatedAwayRef.current = navigatedAway;

  // Seeded from MountOptions (a page load/F5 always reflects FlowBean's own parentOrOverview),
  // but owned client-side from here on — see openOverview/closeOverview below. Deliberately not
  // `options.overviewEntityId ?? ""`: a null overview must render no overview pane at all, not
  // EntityDetailPanel with an empty id (that fallback was the bug this state replaces).
  const [overview, setOverview] = useState<OverviewState | null>(
    options.overviewEntityType != null && options.overviewEntityId != null
      ? { entityType: options.overviewEntityType, entityId: options.overviewEntityId }
      : null,
  );
  // What the address bar must encode (see focusUrl): the main pane's current route — seeded from
  // the server's own resourceUri (keeps e.g. its ?tab=), then replaced on every `navigate` — and
  // the currently open overview. Refs rather than state deps so `navigate` stays referentially
  // stable for the panels that receive it.
  const mainPathRef = useRef<string>(options.main.resourceUri);
  const overviewRef = useRef<OverviewState | null>(overview);
  // Focus mode (see FocusSnapshot): the client-side stack of previous states, plus the `back=` the
  // address bar currently carries — seeded from the server's goBackUrl when the page itself was
  // loaded in focus mode (an F5 loses the stack, so closeFocus then falls back to a real
  // navigation to that URL, exactly like the legacy closeFocusLink).
  const [focusStack, setFocusStackState] = useState<FocusSnapshot[]>([]);
  const focusStackRef = useRef<FocusSnapshot[]>([]);
  const backUrlRef = useRef<string | undefined>(options.goBackUrl);
  const setFocusStack = useCallback((next: FocusSnapshot[]) => {
    focusStackRef.current = next;
    setFocusStackState(next);
  }, []);
  // What FlowBean's parentOrOverview is believed to hold right now (only moves when a bridge call
  // tells the bean), so closeFocus knows whether the bean needs resyncing.
  const serverOverviewRef = useRef<OverviewState | null>(overview);

  const navigate = useCallback((entityType: string, id?: string | number) => {
    const config = getEntityType(entityType);
    if (!config) {
      // Not a migrated entity — no React panel to switch to, so this is a real navigation
      // (matches this app's own url convention: kebab-case entityType as the path segment).
      window.location.href = apiUrl(id != null ? `/${entityType}/${id}` : `/${entityType}`);
      return;
    }
    const path = id != null ? config.routes.detail(id) : config.routes.list;
    mainPathRef.current = path;
    // Leaving the focused entity ends focus mode, same as a JSF navigation (a plain redirect, no
    // `back=`) — there's no longer a promoted panel to put back.
    backUrlRef.current = undefined;
    setFocusStack([]);
    window.history.pushState(null, "", focusUrl(path, overviewPath(overviewRef.current)));
    setNavigatedAway(true);
    setView({ panelKind: id != null ? "detail" : "list", entityType, entityId: id });
  }, [setFocusStack]);

  // Opens (or retargets) the overview pane without touching the main pane (plan §8 phase 5) —
  // the list-row-click replacement for a full `navigate`. Optimistic: React renders the new
  // overview immediately, and the setOverview remoteCommand (fire-and-forget) brings FlowBean's
  // own parentOrOverview back in sync in the background, for F5 and the navigation history.
  const openOverview = useCallback(
    (entityType: string, id: string | number) => {
      const next = { entityType, entityId: id };
      setOverview(next);
      if (options.bridge?.setOverview) {
        options.bridge.setOverview(entityType, id);
        serverOverviewRef.current = next;
      }

      overviewRef.current = next;
      if (mainPathRef.current) {
        window.history.pushState(null, "", focusUrl(mainPathRef.current, overviewPath(next), backUrlRef.current));
      }
    },
    [options],
  );

  const closeOverview = useCallback(() => {
    setOverview(null);
    overviewRef.current = null;
    if (options.bridge?.closeOverview) {
      options.bridge.closeOverview();
      serverOverviewRef.current = null;
    }
    if (mainPathRef.current) {
      window.history.pushState(null, "", focusUrl(mainPathRef.current, undefined, backUrlRef.current));
    }
  }, [options]);

  // Focus mode: the overview entity becomes the main pane, the current main is remembered on the
  // stack. Purely client-side — FlowBean needs no call: it still holds "main = previous main,
  // parentOrOverview = the promoted entity", which is exactly the state closeFocus returns to.
  const enterFocus = useCallback(() => {
    const promoted = overviewRef.current;
    const path = overviewPath(promoted);
    if (!promoted || !path) return;
    const leavingUrl = focusUrl(mainPathRef.current, path, backUrlRef.current);
    setFocusStack([
      ...focusStackRef.current,
      {
        view: viewRef.current,
        mainPath: mainPathRef.current,
        overview: promoted,
        navigatedAway: navigatedAwayRef.current,
        backUrl: backUrlRef.current,
      },
    ]);
    mainPathRef.current = path;
    overviewRef.current = null;
    backUrlRef.current = leavingUrl;
    window.history.pushState(null, "", focusUrl(path, undefined, leavingUrl));
    setOverview(null);
    setNavigatedAway(true);
    setView({ panelKind: "detail", entityType: promoted.entityType, entityId: promoted.entityId });
  }, [setFocusStack]);

  // Puts the promoted entity back in the overview and restores the previous main. With an empty
  // stack (the page itself was loaded in focus mode), only a real navigation to goBackUrl can
  // rebuild that previous state.
  const closeFocus = useCallback(() => {
    const stack = focusStackRef.current;
    const snapshot = stack[stack.length - 1];
    if (!snapshot) {
      if (backUrlRef.current) window.location.href = backUrlRef.current;
      return;
    }
    setFocusStack(stack.slice(0, -1));
    mainPathRef.current = snapshot.mainPath;
    overviewRef.current = snapshot.overview;
    backUrlRef.current = snapshot.backUrl;
    window.history.pushState(
      null,
      "",
      focusUrl(snapshot.mainPath, overviewPath(snapshot.overview), snapshot.backUrl),
    );
    setView(snapshot.view);
    setOverview(snapshot.overview);
    setNavigatedAway(snapshot.navigatedAway);
    // An overview opened/closed while in focus mode moved the bean's parentOrOverview away from
    // the entity going back into the overview pane — put it back, for F5 and the history.
    if (options.bridge?.setOverview && !sameEntity(serverOverviewRef.current, snapshot.overview)) {
      options.bridge.setOverview(snapshot.overview.entityType, snapshot.overview.entityId);
      serverOverviewRef.current = snapshot.overview;
    }
  }, [options, setFocusStack]);

  // Browser back/forward after a client-side navigation: there's no cheap way to reconstruct
  // `view` from an arbitrary URL generically (that's a route-matching concern this app
  // deliberately doesn't have, per the plan's "no router library"), so this falls back to a real
  // navigation — still correct, just not instant, and only on back/forward, not on click.
  useEffect(() => {
    function onPopState() {
      window.location.reload();
    }
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  const hasOverview = overview != null;

  // options.overview is the chrome of the overview JSF mounted with — only valid for that entity.
  // For anything else it's a placeholder until EntityDetailPanel derives the real one from
  // config.detail.chrome once its data loads.
  const overviewChromeFor = (entity: OverviewState): PanelChrome =>
    options.overview &&
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

  const inFocus = focusStack.length > 0;
  const mainToolbar: PanelToolbarSlot = {
    chrome: mainChrome,
    organizationId: inFocus ? (options.overviewOrganizationId ?? options.organizationId) : options.organizationId,
    // Back out of focus mode: the client-side stack, or — page loaded in focus mode — goBackUrl.
    actions: inFocus || (options.goBackUrl && !navigatedAway) ? { closeFocus } : undefined,
  };

  const overviewToolbar: PanelToolbarSlot | undefined = overview
    ? {
        chrome: overviewChromeFor(overview),
        organizationId: options.overviewOrganizationId ?? options.organizationId,
        // closeOverview is App's own wrapper (it also clears the React overview state and the
        // URL); fullscreen is App's client-side focus swap (enterFocus).
        actions: { closeOverview, fullscreen: enterFocus },
      }
    : undefined;

  const content = hasOverview ? (
    // PrimeReact's Splitter sets each SplitterPanel's flex-basis via an inline style, but the
    // `display: flex` its own layout depends on only ever comes from a runtime-injected
    // `@layer primereact` <style> tag — which this app's CSS load order/layer precedence doesn't
    // reliably win against, so without an explicit override here both panels silently fall back
    // to normal block stacking (the overview rendering underneath/inside the main pane instead of
    // beside it). Forcing the same rule inline is robust regardless of that injection's behavior.
    <Splitter style={{ height: "100%", display: "flex", flexWrap: "nowrap" }}>
      <SplitterPanel
        className="panel-splitter-panel-l"
        size={60}
        // Mirrors the legacy panel-docked box's colored top border (focus.xhtml) — scoped to just
        // this pane now that the outer JSF wrapper no longer carries it (that wrapper spans both
        // panes once an overview is open, so putting it there framed the whole splitter instead of
        // the main panel alone). The overview pane needs no equivalent override: .sideview already
        // has its own border-top.
        style={{ display: "flex", flexDirection: "column", minWidth: 0, borderTop: "3px solid var(--main-color)" }}
      >
        <PanelContent
          // Keyed on the entity shown: a focus swap/closeFocus between two entities of the same
          // kind would otherwise reuse the instance and keep the previous one's local state
          // (PanelToolbar's bookmarked flag, active tab).
          key={`${view.panelKind}:${view.entityType}:${view.entityId ?? ""}`}
          view={view}
          organizationId={options.organizationId}
          onNavigate={navigate}
          onOpenOverview={openOverview}
          overview={overview}
          toolbar={mainToolbar}
        />
      </SplitterPanel>
      <SplitterPanel
        className="panel-splitter-panel-r sideview"
        size={40}
        style={{ display: "flex", flexDirection: "column", minWidth: 0 }}
      >
        {overview && (
          <EntityDetailPanel
            entityType={overview.entityType}
            entityId={overview.entityId}
            toolbar={overviewToolbar}
            organizationId={options.overviewOrganizationId ?? options.organizationId}
            // A click inside the overview pane's own fiche (e.g. a row in a relationTab, like a
            // project's UE list) must retarget the OVERVIEW pane, not the main one — even though
            // the click originates from within the overview itself. EntityListPanel's row click
            // prefers onOpenOverview over onNavigate (plan §8 phase 5), so wiring this is what
            // makes that click replace the overview's own entity instead of silently no-op'ing
            // (no onOpenOverview/onNavigate was passed here before — the bug this fixes).
            onOpenOverview={openOverview}
            // Deliberately NOT overviewEntityId={overview.entityId}: a relationTab embedded here
            // (e.g. a project's UE list) renders a DIFFERENT entity type than the overview's own
            // (recordingUnit vs project), and EntityListPanel's row highlighting compares raw ids
            // with no entityType guard — passing the overview's own id through would risk
            // highlighting a row that merely shares that id by coincidence.
            // A sibling jump on the OVERVIEW pane's own fiche retargets the overview in place —
            // never `navigate`, which would move the MAIN pane instead (redirectToFocusOrOverview's
            // own distinction: root panel navigates, non-root panel just retargets the overview).
            onNavigateSibling={(id) => openOverview(overview.entityType, id)}
          />
        )}
      </SplitterPanel>
    </Splitter>
  ) : (
    // Same colored top border as the splitter's left pane above — the no-overview case is just
    // the single-pane equivalent of "the main panel," so it gets the same chrome.
    <div
      className="panel-splitter-panel-l"
      style={{ height: "100%", display: "flex", flexDirection: "column", borderTop: "3px solid var(--main-color)" }}
    >
      <PanelContent
        key={`${view.panelKind}:${view.entityType}:${view.entityId ?? ""}`}
        view={view}
        organizationId={options.organizationId}
        onNavigate={navigate}
        onOpenOverview={openOverview}
        toolbar={mainToolbar}
      />
    </div>
  );

  return (
    <QueryClientProvider client={queryClient}>
      {/* FlowBean.isWriteMode, for every panel below — see panels/writeMode.tsx for why this is a
          context and why it needs no change subscription. */}
      <WriteModeProvider value={options.writeMode === true}>
        <BridgeProvider value={options.bridge}>{content}</BridgeProvider>
      </WriteModeProvider>
    </QueryClientProvider>
  );
}
