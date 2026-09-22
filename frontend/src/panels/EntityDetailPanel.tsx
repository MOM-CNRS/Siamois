import { useQuery } from "@tanstack/react-query";
import { BreadCrumb } from "primereact/breadcrumb";
import { Chip } from "primereact/chip";
import { Panel } from "primereact/panel";
import { TabView, TabPanel } from "primereact/tabview";
import { apiUrl } from "../api/basePath";
import { getEntityType } from "../entities/registry";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import type { PanelToolbarSlot } from "../mountOptions";

export interface EntityDetailPanelProps {
  entityType: string;
  entityId: string | number;
  // Omitted for the overview pane render call that has no toolbar to show, or once the main
  // pane has navigated away from the entity this toolbar was built for (App.tsx).
  toolbar?: PanelToolbarSlot;
  // Makes the breadcrumb's "Projets" crumb switch the pane to that entity's list, the same way
  // EntityListPanel's own row click navigates. Omitted for the overview pane, whose breadcrumb JSF
  // also renders without it being the thing that drives the main pane.
  //
  // This and the next three props are also forwarded into every tab's DetailTabHelpers (plan:
  // generic related-list tab) so a tab rendering an embedded EntityListPanel (relationTab) gets
  // exactly what App.tsx's own top-level list gets — no separate wiring per tab. All four are
  // omitted for the overview pane: there is no overview-within-the-overview, so a related-list
  // tab shown there simply has no onOpenOverview to call and falls back to onNavigate (itself
  // absent too, same as today).
  onNavigate?: (entityType: string, id?: string | number) => void;
  organizationId?: number;
  onOpenOverview?: (entityType: string, id: string | number) => void;
  overviewEntityId?: string | number;
}

/**
 * Generic entity detail — tabs come entirely from config.detail.tabs (plan §3/§4). Project's
 * config registers exactly one tab (the fiche) in phase 4/6; other entities can register more
 * without any change here.
 *
 * The whole thing is one PrimeReact <Panel> (plan §7/§8 follow-up: "the toolbar is part of the
 * panel header, panels should be React panels themselves" / "reproduce the JSF header: title +
 * toolbar"). Its `header` is a single PanelHeaderBar — icon + entity singular label as the title
 * (matching EntityListPanel's own icon+label header), followed by config.detail.header?.(entity,
 * helpers) (actionUnitPanelHeader.xhtml's identifier/type/name/location chips), with the
 * toolbar on the right — one node, not split across <Panel>'s header/icons props.
 */
export function EntityDetailPanel({
  entityType,
  entityId,
  toolbar,
  onNavigate,
  organizationId,
  onOpenOverview,
  overviewEntityId,
}: EntityDetailPanelProps) {
  const config = getEntityType(entityType);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["entity-detail", entityType, entityId],
    queryFn: () => config!.api.get(entityId),
    enabled: config != null,
  });

  if (!config) {
    return <div className="entity-detail-panel-unsupported">Unknown entity type &quot;{entityType}&quot;</div>;
  }
  if (isLoading) {
    return <div className="entity-detail-panel-loading">Loading…</div>;
  }
  if (error) {
    return <div className="entity-detail-panel-error">{(error as Error).message}</div>;
  }
  if (!data) {
    return null;
  }

  const helpers = { refetch: () => void refetch(), organizationId, onNavigate, onOpenOverview, overviewEntityId };

  // A toolbar built server-side (MountOptions) has no entity data to derive chrome from at that
  // point — it's the initial mount's own entity. Once we have the fetched entity, prefer chrome
  // freshly derived from it via config.detail.chrome when the entity type registers one: this is
  // what makes a client-opened overview's toolbar (plan §8 phase 5, EntityListPanel's
  // onOpenOverview — no server round-trip to build a toolbar from) possible at all, and it also
  // keeps title/bookmark state current after an in-place edit for the originally-seeded overview.
  const resolvedToolbar: PanelToolbarSlot | undefined = toolbar && {
    ...toolbar,
    chrome: config.detail.chrome?.(data) ?? toolbar.chrome,
  };

  return (
    <Panel
      className="entity-detail-panel"
      header={
        <PanelHeaderBar
          title={
            <>
              <i className={config.icon} style={{ fontSize: "2rem", color: "var(--main-color)" }} />
              <span style={{ paddingRight: "0.5em" }}>{config.labels.singular}</span>
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
      <BreadCrumb
        className="panel-bc"
        // createHomeItem: icon only, no label, and a real redirect to the dashboard
        // (FlowBean.redirectToDashboard → /focus/L3dlbGNvbWU=, the base64url of "/welcome"). Not
        // routed through onNavigate: there is no "home" entity type for the registry to resolve,
        // and the dashboard is a JSF page, not one of the three React panel kinds.
        home={{ icon: "bi bi-house", url: apiUrl("/focus/L3dlbGNvbWU=") }}
        model={[
          {
            // ActionUnitPanel.createRootTypeItem: "Tous les projets", linking to the entity's list.
            label: `Tous les ${config.labels.plural.toLowerCase()}`,
            icon: config.icon,
            command: onNavigate ? () => onNavigate(entityType) : undefined,
          },
        ]}
      />
      <TabView className="entity-detail-panel-tabs">
        {config.detail.tabs.map((tab) => {
          const badge = tab.badge?.(data);
          return (
            <TabPanel
              key={tab.key}
              header={
                badge == null ? (
                  tab.label
                ) : (
                  // actionUnitTabView.xhtml's own count pastille next to the tab label
                  // (panelModel.unit.recordingUnitCount) — a plain Chip, not PrimeReact's Badge,
                  // matching the tab-header chips elsewhere in this app (PanelHeaderBar's own
                  // count chip on the list panel).
                  <span className="entity-detail-panel-tab-header">
                    {tab.label} <Chip label={String(badge)} />
                  </span>
                )
              }
            >
              {tab.render(data, helpers)}
            </TabPanel>
          );
        })}
      </TabView>
    </Panel>
  );
}
