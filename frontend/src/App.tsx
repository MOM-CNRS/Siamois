import { useCallback, useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Splitter, SplitterPanel } from "primereact/splitter";
import type { MountOptions, PanelKind, PanelToolbarSlot } from "./mountOptions";
import { apiUrl } from "./api/basePath";
import { getAllEntityTypes, getEntityType } from "./entities/registry";
import { EntityListPanel } from "./panels/EntityListPanel";
import { EntityDetailPanel } from "./panels/EntityDetailPanel";
import { HomePanel } from "./panels/HomePanel";

const queryClient = new QueryClient();

// What's actually on screen in the main pane right now — starts from the mount options JSF gave
// us, but from then on this is owned entirely client-side (see App below). Deliberately NOT the
// same object as MountOptions: this is the one part of it that changes after mount.
interface NavigationState {
  panelKind: PanelKind;
  entityType: string;
  entityId?: string | number;
}

// Routes to the one generic panel matching panelKind — no per-entity branching here either,
// that all lives inside the three panels themselves via the registry (plan §3).
function PanelContent({
  view,
  organizationId,
  onNavigate,
  onCreate,
  toolbar,
}: {
  view: NavigationState;
  organizationId?: number;
  onNavigate: (entityType: string, id?: string | number) => void;
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
          onCreate={onCreate}
          toolbar={toolbar}
        />
      );
    case "detail":
      if (view.entityId == null) {
        return <div className="entity-detail-panel-error">Missing entityId for detail panel</div>;
      }
      return <EntityDetailPanel entityType={view.entityType} entityId={view.entityId} toolbar={toolbar} />;
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
 * remoteCommand actions (duplicate/create/refresh/settings, bound to that specific panel bean)
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

  const navigate = useCallback((entityType: string, id?: string | number) => {
    const config = getEntityType(entityType);
    if (!config) {
      // Not a migrated entity — no React panel to switch to, so this is a real navigation
      // (matches this app's own url convention: kebab-case entityType as the path segment).
      window.location.href = apiUrl(id != null ? `/${entityType}/${id}` : `/${entityType}`);
      return;
    }
    const path = id != null ? config.routes.detail(id) : config.routes.list;
    window.history.pushState(null, "", apiUrl(path));
    setNavigatedAway(true);
    setView({ panelKind: id != null ? "detail" : "list", entityType, entityId: id });
  }, []);

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

  const hasOverview = options.overviewEntityType != null;

  const mainToolbar: PanelToolbarSlot | undefined = navigatedAway
    ? undefined
    : { chrome: options.main, organizationId: options.organizationId, actions: options.actions };

  const content = hasOverview ? (
    <Splitter style={{ height: "100%" }}>
      <SplitterPanel className="panel-splitter-panel-l" size={60}>
        <PanelContent
          view={view}
          organizationId={options.organizationId}
          onNavigate={navigate}
          onCreate={navigatedAway ? undefined : options.actions?.listCreate}
          toolbar={mainToolbar}
        />
      </SplitterPanel>
      <SplitterPanel className="panel-splitter-panel-r sideview" size={40}>
        {options.overviewEntityType && (
          <EntityDetailPanel
            entityType={options.overviewEntityType}
            entityId={options.overviewEntityId ?? ""}
            toolbar={
              options.overview && {
                chrome: options.overview,
                organizationId: options.overviewOrganizationId,
                actions: options.overviewActions,
              }
            }
          />
        )}
      </SplitterPanel>
    </Splitter>
  ) : (
    <div className="panel-splitter-panel-l">
      <PanelContent
        view={view}
        organizationId={options.organizationId}
        onNavigate={navigate}
        onCreate={navigatedAway ? undefined : options.actions?.listCreate}
        toolbar={mainToolbar}
      />
    </div>
  );

  return <QueryClientProvider client={queryClient}>{content}</QueryClientProvider>;
}
