import { useCallback, useEffect, useReducer, useRef } from "react";
import { apiUrl } from "../api/basePath";
import { getEntityType } from "../entities/registry";
import type { MountOptions } from "../mountOptions";
import type { EntityPreview } from "../panels/EntityDetailPanel";
import { withPanelTransition } from "../panels/pane/panelTransition";
import type { ListSeed } from "./externalBus";
import { isMobileViewport } from "../viewport";
import { listPath } from "./paths";
import { recordVisit } from "./navTrail";
import { decodeUrl, encodeUrl, initialNavState, navReducer, overviewPath, serverSync, transitionOf, type NavAction, type NavState } from "./reducer";

export interface Navigation {
  state: NavState;
  // Stable identities: panels receive them, and must not re-render on every navigation.
  navigate: (entityType: string, id?: string | number, preview?: EntityPreview) => void;
  openOverview: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  // The entity type's list in the main pane, on a saved view (or, without one, its default list).
  openView: (entityType: string, viewId?: number) => void;
  // The entity type's list in the overview pane, started on this search (the search bar's « Voir tout »).
  openList: (entityType: string, seed: Omit<ListSeed, "nonce">) => void;
  // The page at this address (a Back/Forward trail entry): the panes are rebuilt from it, no page load.
  restoreUrl: (url: string) => void;
  closeOverview: () => void;
  enterFocus: () => void;
  closeFocus: () => void;
}

/**
 * The main pane's client-side router, on top of the pure reducer: it keeps the address bar
 * (pushState, and Back/Forward decoded from it, with no page load), and the JSF session
 * (FlowBean's main and overview panels, through the bridge) following the state.
 */
export function useNavigation(options: MountOptions): Navigation {
  const [state, dispatch] = useReducer(navReducer, options, initialNavState);
  // Only read by the callbacks below, at call time: their identity must not follow the state.
  const latest = useRef({ state, options });
  latest.current = { state, options };

  const send = useCallback((action: NavAction) => {
    const transition = transitionOf(latest.current.state, action);
    if (transition) withPanelTransition(transition, () => dispatch(action));
    else dispatch(action);
  }, []);

  const navigate = useCallback<Navigation["navigate"]>(
    (entityType, id, preview) => {
      const config = getEntityType(entityType);
      if (!config) {
        // Not a migrated entity: no React panel to switch to, so this is a real navigation (this
        // app's own url convention: kebab-case entityType as the path segment).
        window.location.href = apiUrl(id != null ? `/${entityType}/${id}` : `/${entityType}`);
        return;
      }
      const path = id != null ? config.routes.detail(id) : config.routes.list;
      send({ type: "navigate", path, view: { panelKind: id != null ? "detail" : "list", entityType, entityId: id, preview } });
    },
    [send],
  );
  const openView = useCallback<Navigation["openView"]>(
    (entityType, viewId) => {
      const path = listPath(entityType, viewId);
      if (path == null) return;
      send({ type: "navigate", path, view: { panelKind: "list", entityType, viewId } });
    },
    [send],
  );
  const openOverview = useCallback<Navigation["openOverview"]>(
    (entityType, id, preview) => {
      // Phone width: no room for two panes, so whatever would open beside the list opens in the main pane.
      if (isMobileViewport()) navigate(entityType, id, preview);
      else send({ type: "openOverview", overview: { entityType, entityId: id, preview } });
    },
    [send, navigate],
  );
  const seedNonce = useRef(0);
  const openList = useCallback<Navigation["openList"]>(
    (entityType, seed) => send({ type: "openOverview", overview: { entityType, seed: { ...seed, nonce: ++seedNonce.current } } }),
    [send],
  );
  const closeOverview = useCallback(() => send({ type: "closeOverview" }), [send]);
  const enterFocus = useCallback(() => send({ type: "enterFocus" }), [send]);
  const closeFocus = useCallback(() => {
    const { focusStack, backUrl } = latest.current.state;
    // The page itself was loaded in focus mode: nothing client-side to go back to.
    if (focusStack.length === 0) {
      if (backUrl) window.location.href = backUrl;
      return;
    }
    send({ type: "closeFocus" });
  }, [send]);

  const restoreUrl = useCallback<Navigation["restoreUrl"]>((url) => {
    const restore = decodeUrl(new URL(url, window.location.origin), initialNavState(latest.current.options));
    if (!restore) {
      window.location.assign(url);
      return;
    }
    window.history.pushState(null, "", url);
    dispatch(restore);
  }, []);

  // Every page the user lands on goes in the header's Back/Forward trail.
  useEffect(() => {
    if (!state.mainPath) return;
    recordVisit({ url: encodeUrl(state), mainPath: state.mainPath, overviewPath: overviewPath(state.overview) });
  }, [state]);

  // The address bar follows every app-driven change; a Back/Forward already moved it.
  useEffect(() => {
    if (state.urlOwner === "app" && state.mainPath) window.history.pushState(null, "", encodeUrl(state));
  }, [state]);

  // Browser Back/Forward: rebuild the state from the address, no page load. An address this app
  // can't read (an entity it doesn't show, a foreign URL) is the one case left for a real reload.
  useEffect(() => {
    function onPopState() {
      const restore = decodeUrl(window.location, initialNavState(latest.current.options));
      if (restore) dispatch(restore);
      else window.location.reload();
    }
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  // The JSF session follows the main and overview panes (history sidebar, the pairing of a later
  // overview, F5): fire-and-forget, and only for what actually moved.
  const server = useRef<Pick<NavState, "mainPath" | "overview">>(state);
  useEffect(() => {
    const { bridge } = latest.current.options;
    const sync = serverSync(server.current, state);
    if (sync.closeOverview) bridge?.closeOverview?.();
    if (sync.setMain) bridge?.setMain?.(sync.setMain);
    if (sync.setOverview) bridge?.setOverview?.(sync.setOverview.entityType, sync.setOverview.entityId);
    server.current = { mainPath: state.mainPath, overview: state.overview };
  }, [state]);

  return { state, navigate, openView, openOverview, openList, restoreUrl, closeOverview, enterFocus, closeFocus };
}
