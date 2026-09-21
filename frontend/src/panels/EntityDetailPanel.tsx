import { useQuery } from "@tanstack/react-query";
import { Panel } from "primereact/panel";
import { TabView, TabPanel } from "primereact/tabview";
import { getEntityType } from "../entities/registry";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import type { PanelToolbarSlot } from "../mountOptions";

export interface EntityDetailPanelProps {
  entityType: string;
  entityId: string | number;
  // Omitted for the overview pane render call that has no toolbar to show, or once the main
  // pane has navigated away from the entity this toolbar was built for (App.tsx).
  toolbar?: PanelToolbarSlot;
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
export function EntityDetailPanel({ entityType, entityId, toolbar }: EntityDetailPanelProps) {
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

  const helpers = { refetch: () => void refetch() };

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
          toolbar={toolbar}
        />
      }
    >
      <TabView>
        {config.detail.tabs.map((tab) => (
          <TabPanel key={tab.key} header={tab.label}>
            {tab.render(data, helpers)}
          </TabPanel>
        ))}
      </TabView>
    </Panel>
  );
}
