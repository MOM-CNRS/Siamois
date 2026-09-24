import { useCallback, useEffect, useRef, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Splitter, SplitterPanel } from "primereact/splitter";
import type { MountOptions, PanelActions, PanelChrome, PanelKind, PanelToolbarSlot } from "./mountOptions";
import { apiUrl } from "./api/basePath";
import { getAllEntityTypes, getEntityType } from "./entities/registry";
import { EntityListPanel } from "./panels/EntityListPanel";
import { EntityDetailPanel } from "./panels/EntityDetailPanel";
import { HomePanel } from "./panels/HomePanel";
import { WriteModeProvider } from "./panels/writeMode";

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
 * One real constraint this runs into: the main toolbar (`options.main`/`options.actions`) is
 * built once, server-side, for the ONE entity focus.xhtml actually mounted — both the bridged
 * remoteCommand actions (duplicate/create/settings, bound to that specific panel bean)
 * and the bookmark state/resourceUri/title (read off that entity at mount time). Once `navigate`
 * moves the view to a different entity, none of that is valid for the new one anymore — there's
 * no server round-trip to refresh it from. Rather than show a toolbar acting on the wrong entity
 * (bridged actions silently no-op on the old one, bookmark toggling the wrong resourceUri), the
 * main toolbar is hidden entirely after the first client-side navigation. The overview pane's
 * own toolbar is unaffected — it never re-targets, since only the main pane navigates.
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
  // True between a setOverview bridge call and its ajax completion — see the
  // "siamois-set-overview-done" listener below. Guards against a fast double-click on the
  // overview toolbar acting on the previous entity while the bean is still catching up (plan §8
  // phase 5's own caveat about the remoteCommand being async).
  const [overviewBusy, setOverviewBusy] = useState(false);

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
  // What FlowBean's parentOrOverview is believed to hold right now. Only moves when a bridge call
  // actually tells the bean (setOverview/closeOverview). In focus mode, it's what decides whether
  // the bridged overview actions (bound to parentOrOverview) can act for the promoted main entity.
  const [serverOverview, setServerOverviewState] = useState<OverviewState | null>(overview);
  const serverOverviewRef = useRef<OverviewState | null>(overview);
  const setServerOverview = useCallback((next: OverviewState | null) => {
    serverOverviewRef.current = next;
    setServerOverviewState(next);
  }, []);

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
  // overview immediately, and the setOverview remoteCommand (fire-and-forget, no promise to
  // await) brings FlowBean's own parentOrOverview back in sync in the background, which is what
  // keeps F5 and the three still-gated overview actions (duplicate/create/settings) correct.
  const openOverview = useCallback(
    (entityType: string, id: string | number) => {
      setOverview({ entityType, entityId: id });
      // Only actually goes "in flight" when there's a bridge call to wait on — for an entity
      // type the bridge doesn't cover (setOverview absent from MountOptions.actions), there is no
      // completion event to ever clear this, so it must not be set in the first place.
      const next = { entityType, entityId: id };
      if (options.actions?.setOverview) {
        setOverviewBusy(true);
        options.actions.setOverview(entityType, id);
        setServerOverview(next);
      }

      overviewRef.current = next;
      if (mainPathRef.current) {
        window.history.pushState(null, "", focusUrl(mainPathRef.current, overviewPath(next), backUrlRef.current));
      }
    },
    [options, setServerOverview],
  );

  const closeOverview = useCallback(() => {
    setOverview(null);
    overviewRef.current = null;
    if (options.overviewActions?.closeOverview) {
      options.overviewActions.closeOverview();
      setServerOverview(null);
    }
    if (mainPathRef.current) {
      window.history.pushState(null, "", focusUrl(mainPathRef.current, undefined, backUrlRef.current));
    }
  }, [options, setServerOverview]);

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
    // the entity going back into the overview pane — put it back, or F5 and the overview's
    // bridged actions would target the wrong entity.
    if (options.actions?.setOverview && !sameEntity(serverOverviewRef.current, snapshot.overview)) {
      setOverviewBusy(true);
      options.actions.setOverview(snapshot.overview.entityType, snapshot.overview.entityId);
      setServerOverview(snapshot.overview);
    }
  }, [options, setFocusStack, setServerOverview]);

  // The setOverview remoteCommand's oncomplete dispatches this plain DOM event (reactPanelActions.xhtml)
  // since a p:remoteCommand call gives the caller no promise to await — this is what lets the
  // overview toolbar re-enable itself precisely when the bean is back in sync, rather than never
  // or after a guessed timeout. Filtered by panelIndex in case a page ever mounts more than one
  // panel instance; focus.xhtml only ever has one.
  useEffect(() => {
    function onSetOverviewDone(e: Event) {
      const detail = (e as CustomEvent<{ panelIndex?: string }>).detail;
      if (options.panelIndex == null || detail?.panelIndex === options.panelIndex) {
        setOverviewBusy(false);
      }
    }
    window.addEventListener("siamois-set-overview-done", onSetOverviewDone);
    return () => window.removeEventListener("siamois-set-overview-done", onSetOverviewDone);
  }, [options.panelIndex]);

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

  const inFocus = focusStack.length > 0;
  const mainToolbar: PanelToolbarSlot | undefined = inFocus
    ? (() => {
        const promoted: OverviewState = { entityType: view.entityType, entityId: view.entityId ?? "" };
        // The bean's overview actions are bound to parentOrOverview, which is still the promoted
        // entity — until an overview opened from within focus mode (or its close) moves it.
        const bridged = !overviewBusy && sameEntity(serverOverview, promoted);
        const oa = options.overviewActions;
        const actions: PanelActions = bridged && oa
          ? { duplicate: oa.duplicate, create: oa.create, settings: oa.settings, closeFocus }
          : { closeFocus };
        return {
          chrome: overviewChromeFor(promoted),
          organizationId: options.overviewOrganizationId ?? options.organizationId,
          actions,
        };
      })()
    : navigatedAway
      ? undefined
      : {
          chrome: options.main,
          organizationId: options.organizationId,
          actions: options.goBackUrl ? { ...options.actions, closeFocus } : options.actions,
        };

  // chrome here is only ever what EntityDetailPanel falls back to before config.detail.chrome
  // has data to derive from (Project registers one; see config.tsx) — for the overview seeded by
  // MountOptions, options.overview already has the real thing. A placeholder is enough for a
  // client-opened overview because EntityDetailPanel doesn't render a header at all until its own
  // query resolves, at which point config.detail.chrome (when the entity type has one) replaces it.
  const overviewToolbar: PanelToolbarSlot | undefined = overview
    ? {
        chrome: overviewChromeFor(overview),
        organizationId: options.overviewOrganizationId ?? options.organizationId,
        // Omitted (not just disabled) while a setOverview call is in flight — PanelToolbar only
        // ever checks whether an action callback is present, so this is what "unavailable" means
        // for it (plan §8 phase 5's in-flight caveat). closeOverview is App's own wrapper, not
        // the raw bridged function directly — it also clears the React overview state and the
        // URL, neither of which the bridged closeOverview (server bean + legacy hideSideview JS
        // only) knows about. reactAction_overview_closeOverview is always-rendered server-side
        // now (see reactPanelActions.xhtml), so options.overviewActions?.closeOverview is only
        // ever missing for an entity type React doesn't bridge overview actions for at all.
        // fullscreen is App's own client-side focus swap (enterFocus), not a bridged call.
        actions: overviewBusy
          ? undefined
          : { ...options.overviewActions, closeOverview, fullscreen: enterFocus },
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
          onCreate={navigatedAway ? undefined : options.actions?.listCreate}
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
            // Same reason the overview toolbar's own actions are withheld while a setOverview
            // bridge call is in flight — a sibling jump fired mid-flight would race it.
            siblingNavDisabled={overviewBusy}
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
        onCreate={navigatedAway ? undefined : options.actions?.listCreate}
        toolbar={mainToolbar}
      />
    </div>
  );

  return (
    <QueryClientProvider client={queryClient}>
      {/* FlowBean.isWriteMode, for every panel below — see panels/writeMode.tsx for why this is a
          context and why it needs no change subscription. */}
      <WriteModeProvider value={options.writeMode === true}>{content}</WriteModeProvider>
    </QueryClientProvider>
  );
}
