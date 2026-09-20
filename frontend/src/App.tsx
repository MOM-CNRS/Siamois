import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Splitter, SplitterPanel } from "primereact/splitter";
import type { MountOptions } from "./mountOptions";
import { getAllEntityTypes } from "./entities/registry";
import { PanelToolbar } from "./components/PanelToolbar";
import { EntityListPanel } from "./panels/EntityListPanel";
import { EntityDetailPanel } from "./panels/EntityDetailPanel";
import { HomePanel } from "./panels/HomePanel";

const queryClient = new QueryClient();

// Routes to the one generic panel matching panelKind — no per-entity branching here either,
// that all lives inside the three panels themselves via the registry (plan §3).
function PanelContent({
  options,
  entityType,
  entityId,
}: {
  options: MountOptions;
  entityType: string;
  entityId?: string | number;
}) {
  switch (options.panelKind) {
    case "home": {
      // Assembled from every registered entity's own `home.widgets` (plan §8 phase 7) — not a
      // switch/import per entity, same "registry, not a switch" principle List/Detail already
      // follow. Most entities won't declare `home` at all yet, so this stays empty until they do.
      const widgets = getAllEntityTypes().flatMap(
        (config) =>
          config.home?.widgets({ organizationId: options.organizationId, onNavigate: options.onNavigate }) ?? [],
      );
      return <HomePanel widgets={widgets} />;
    }
    case "list":
      return (
        <EntityListPanel
          entityType={entityType}
          organizationId={options.organizationId}
          onNavigate={options.onNavigate}
        />
      );
    case "detail":
      if (entityId == null) {
        return <div className="entity-detail-panel-error">Missing entityId for detail panel</div>;
      }
      return <EntityDetailPanel entityType={entityType} entityId={entityId} />;
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
 * toolbars (focus.xhtml's for the main panel, panelContent.xhtml's for the overview) — PanelToolbar
 * is the same generic component for both, parameterized by `chrome`/`actions`.
 */
export function App({ options }: { options: MountOptions }) {
  const hasOverview = options.overviewEntityType != null;

  const content = hasOverview ? (
    <Splitter style={{ height: "100%" }}>
      <SplitterPanel className="panel-splitter-panel-l" size={60}>
        <PanelToolbar chrome={options.main} organizationId={options.organizationId} actions={options.actions} />
        <PanelContent options={options} entityType={options.entityType} entityId={options.entityId} />
      </SplitterPanel>
      <SplitterPanel className="panel-splitter-panel-r sideview" size={40}>
        <div className="sideview-titlebar">
          {options.overview && (
            <PanelToolbar
              chrome={options.overview}
              organizationId={options.overviewOrganizationId}
              actions={options.overviewActions}
            />
          )}
        </div>
        {options.overviewEntityType && (
          <EntityDetailPanel
            entityType={options.overviewEntityType}
            entityId={options.overviewEntityId ?? ""}
          />
        )}
      </SplitterPanel>
    </Splitter>
  ) : (
    <div className="panel-splitter-panel-l">
      <PanelToolbar chrome={options.main} organizationId={options.organizationId} actions={options.actions} />
      <PanelContent options={options} entityType={options.entityType} entityId={options.entityId} />
    </div>
  );

  return <QueryClientProvider client={queryClient}>{content}</QueryClientProvider>;
}
