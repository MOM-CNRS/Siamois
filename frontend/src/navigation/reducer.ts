import type { MountOptions, PanelKind } from "../mountOptions";
import type { EntityPreview } from "../panels/EntityDetailPanel";
import type { PanelTransitionKind } from "../panels/panelTransition";
import { entityPath, resolveEntityPath } from "./paths";
import { decodeFocusUrl, encodeFocusUrl } from "./url";

// What's actually on screen in the main pane right now: starts from the mount options JSF gave us,
// but from then on this is owned entirely client-side. Deliberately NOT the same object as
// MountOptions: this is the one part of it that changes after mount.
export interface NavigationView {
  panelKind: PanelKind;
  entityType: string;
  entityId?: string | number;
  // What the opener already knew about the entity (a list row, a sibling's label): shown in the
  // fiche's header while the entity itself loads.
  preview?: EntityPreview;
}

// What's open in the right-hand overview pane right now.
export interface OverviewState {
  entityType: string;
  entityId: string | number;
  preview?: EntityPreview;
}

// One level of focus mode: the overview entity was promoted to the main pane, and this is what
// closeFocus restores. A stack of these, not a single slot: focusing again from within focus mode
// just pushes another level, the same way JSF's own `back=` param nests.
interface FocusSnapshot {
  view: NavigationView;
  mainPath: string;
  // The entity that was promoted: it goes back into the overview pane on closeFocus.
  overview: OverviewState;
  // Restored as-is, so the original server-built main toolbar comes back only if it was still
  // valid when focus mode was entered.
  navigatedAway: boolean;
  // The `back=` of the state being left, so a nested closeFocus puts the right URL back.
  backUrl?: string;
}

export interface NavState {
  view: NavigationView;
  // The main pane's resource path: seeded from the server's own (keeps e.g. its ?tab=), then the
  // registry's on every navigation. It is what the address bar encodes.
  mainPath: string;
  overview: OverviewState | null;
  // Focus mode's way back (the `back=` param): set while an entity is promoted to the main pane, or
  // when the page itself was loaded in focus mode: then the stack below is empty (an F5 loses it)
  // and closeFocus falls back to a real navigation to that URL, like the legacy closeFocusLink.
  backUrl?: string;
  focusStack: FocusSnapshot[];
  // False while the main pane still shows the view JSF mounted, whose server-built chrome is valid.
  navigatedAway: boolean;
  // Who moved the address bar to this state: the app (push a history entry for it) or the browser
  // (Back/Forward: the address is already right).
  urlOwner: "app" | "browser";
}

export type NavAction =
  | { type: "navigate"; view: NavigationView; path: string }
  | { type: "openOverview"; overview: OverviewState }
  | { type: "closeOverview" }
  | { type: "enterFocus" }
  | { type: "closeFocus" }
  | { type: "restore"; state: Pick<NavState, "view" | "mainPath" | "overview" | "backUrl" | "navigatedAway"> };

export function sameEntity(a: OverviewState | null | undefined, b: OverviewState | null | undefined): boolean {
  return a != null && b != null && a.entityType === b.entityType && String(a.entityId) === String(b.entityId);
}

export function overviewPath(overview: OverviewState | null): string | undefined {
  return overview ? entityPath(overview.entityType, overview.entityId) : undefined;
}

export function initialNavState(options: MountOptions): NavState {
  return {
    view: { panelKind: options.panelKind, entityType: options.entityType, entityId: options.entityId },
    mainPath: options.main.resourceUri,
    // Not `?? ""`: a null overview must render no overview pane at all.
    overview:
      options.overviewEntityType != null && options.overviewEntityId != null
        ? { entityType: options.overviewEntityType, entityId: options.overviewEntityId }
        : null,
    backUrl: options.goBackUrl,
    focusStack: [],
    navigatedAway: false,
    urlOwner: "browser",
  };
}

/** The address bar for this state. The main token is the main pane's CURRENT view: an F5 replays it. */
export function encodeUrl(state: NavState): string {
  return encodeFocusUrl({ mainPath: state.mainPath, overviewPath: overviewPath(state.overview), backUrl: state.backUrl });
}

/**
 * The state a browser Back/Forward lands on, read from the address alone (no preview, no focus
 * stack: like an F5). Null when the address isn't one this app wrote, or names an entity it can't
 * show: the caller then reloads, which is always correct.
 */
export function decodeUrl(location: { pathname: string; search: string }, initial: NavState): NavAction | null {
  const parts = decodeFocusUrl(location);
  if (!parts) return null;

  let view: NavigationView;
  if (parts.mainPath === initial.mainPath) {
    // The view JSF mounted with (Home has no route of its own).
    view = initial.view;
  } else {
    const resolved = resolveEntityPath(parts.mainPath);
    if (!resolved) return null;
    view = { panelKind: resolved.entityId != null ? "detail" : "list", ...resolved };
  }

  let overview: OverviewState | null = null;
  if (parts.overviewPath) {
    const resolved = resolveEntityPath(parts.overviewPath);
    if (resolved?.entityId == null) return null;
    overview = { entityType: resolved.entityType, entityId: resolved.entityId };
  }

  return {
    type: "restore",
    state: {
      view,
      mainPath: parts.mainPath,
      overview,
      backUrl: parts.backUrl,
      navigatedAway: parts.mainPath !== initial.mainPath,
    },
  };
}

export function navReducer(state: NavState, action: NavAction): NavState {
  switch (action.type) {
    case "navigate":
      // Leaving the focused entity ends focus mode, same as a JSF navigation (a plain redirect,
      // no `back=`): there's no longer a promoted panel to put back.
      return { ...state, view: action.view, mainPath: action.path, backUrl: undefined, focusStack: [], navigatedAway: true, urlOwner: "app" };

    case "openOverview":
      return { ...state, overview: action.overview, urlOwner: "app" };

    case "closeOverview":
      return { ...state, overview: null, urlOwner: "app" };

    case "enterFocus": {
      // The overview entity becomes the main pane, the current main is remembered on the stack.
      const promoted = state.overview;
      const path = overviewPath(promoted);
      if (!promoted || !path) return state;
      return {
        view: { panelKind: "detail", entityType: promoted.entityType, entityId: promoted.entityId },
        mainPath: path,
        overview: null,
        backUrl: encodeUrl(state),
        focusStack: [
          ...state.focusStack,
          { view: state.view, mainPath: state.mainPath, overview: promoted, navigatedAway: state.navigatedAway, backUrl: state.backUrl },
        ],
        navigatedAway: true,
        urlOwner: "app",
      };
    }

    case "closeFocus": {
      // Puts the promoted entity back in the overview and restores the previous main. With an
      // empty stack only a real navigation to backUrl can rebuild that state (see NavState).
      const snapshot = state.focusStack[state.focusStack.length - 1];
      if (!snapshot) return state;
      return {
        view: snapshot.view,
        mainPath: snapshot.mainPath,
        overview: snapshot.overview,
        backUrl: snapshot.backUrl,
        focusStack: state.focusStack.slice(0, -1),
        navigatedAway: snapshot.navigatedAway,
        urlOwner: "app",
      };
    }

    case "restore":
      return { ...action.state, focusStack: [], urlOwner: "browser" };
  }
}

/** The pane animation an action gets, if any. Only the opening animates: retargeting just swaps content. */
export function transitionOf(state: NavState, action: NavAction): PanelTransitionKind | undefined {
  switch (action.type) {
    case "openOverview":
      return state.overview == null ? "overview-open" : undefined;
    case "closeOverview":
      return "overview-close";
    case "enterFocus":
      return state.overview ? "focus-enter" : undefined;
    case "closeFocus":
      return state.focusStack.length > 0 ? "focus-exit" : undefined;
    default:
      return undefined;
  }
}

/** The calls the JSF session needs to follow `prev` → `next`, in the order it needs them. */
export interface ServerSync {
  closeOverview: boolean;
  setMain?: string;
  setOverview?: OverviewState;
}

/**
 * What FlowBean's parentOrOverview and main panel must be told (fire-and-forget, for F5 and the
 * history sidebar). Order matters server-side: an overview going away is closed before the main
 * moves (or the promoted main would keep it attached), and an overview coming back is set after (it
 * is paired with the main pane on screen).
 */
export function serverSync(server: Pick<NavState, "mainPath" | "overview">, next: Pick<NavState, "mainPath" | "overview">): ServerSync {
  const overviewChanged = !sameEntity(server.overview, next.overview) && (server.overview != null || next.overview != null);
  return {
    closeOverview: overviewChanged && next.overview == null,
    setMain: next.mainPath !== server.mainPath ? next.mainPath : undefined,
    setOverview: overviewChanged && next.overview ? next.overview : undefined,
  };
}
