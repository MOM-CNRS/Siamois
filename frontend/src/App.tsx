import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Splitter, SplitterPanel } from "primereact/splitter";
import type { MountOptions } from "./mountOptions";
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
    case "home":
      // Widget list is empty until phase 7 (Home) registers the recent/my-projects + Project
      // card widgets — HomePanel already renders correctly with none.
      return <HomePanel widgets={[]} />;
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
 */
export function App({ options }: { options: MountOptions }) {
  const hasOverview = options.overviewEntityType != null;

  const content = hasOverview ? (
    <Splitter style={{ height: "100%" }}>
      <SplitterPanel className="panel-splitter-panel-l" size={60}>
        <PanelContent options={options} entityType={options.entityType} entityId={options.entityId} />
      </SplitterPanel>
      <SplitterPanel className="panel-splitter-panel-r sideview" size={40}>
        <div className="sideview-titlebar">Overview: {options.overviewEntityType}</div>
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
      <PanelContent options={options} entityType={options.entityType} entityId={options.entityId} />
    </div>
  );

  return <QueryClientProvider client={queryClient}>{content}</QueryClientProvider>;
}
