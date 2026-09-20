import { useQuery } from "@tanstack/react-query";
import { TabView, TabPanel } from "primereact/tabview";
import { getEntityType } from "../entities/registry";

export interface EntityDetailPanelProps {
  entityType: string;
  entityId: string | number;
}

/**
 * Generic entity detail — tabs come entirely from config.detail.tabs (plan §3/§4). Project's
 * config registers exactly one tab (the fiche) in phase 4/6; other entities can register more
 * without any change here. Whether a single-tab case should suppress the tab bar to match JSF's
 * actual chrome is a phase-6 question — audit the real actionUnitTabView.xhtml markup then,
 * don't assume it here.
 */
export function EntityDetailPanel({ entityType, entityId }: EntityDetailPanelProps) {
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

  return (
    <TabView>
      {config.detail.tabs.map((tab) => (
        <TabPanel key={tab.key} header={tab.label}>
          {tab.render(data, { refetch: () => void refetch() })}
        </TabPanel>
      ))}
    </TabView>
  );
}
