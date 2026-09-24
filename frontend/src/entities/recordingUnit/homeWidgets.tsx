import { EntityCountCard } from "../../components/home/EntityCountCard";
import { useOrganizationCounts } from "../organizationCounts";
import type { HomeWidgetContext, HomeWidgetDef } from "../types";

// homePanel.xhtml's own card for this type — opens the organization-wide React list (no id →
// App's client-side router, no page load).
function RecordingUnitCountCardWidget({ organizationId, onNavigate }: HomeWidgetContext) {
  const { data } = useOrganizationCounts(organizationId);

  return (
    <EntityCountCard
      icon="bi bi-pencil-square"
      label="Unités d'enregistrement"
      description="Les unités d'enregistrement"
      count={data?.recordingUnits}
      className="sia-welcome-card sia-recording-unit"
      chipClassName="recording-unit-count-chip-alt"
      onOpen={() => onNavigate?.("recordingUnit")}
    />
  );
}

export function recordingUnitHomeWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [{ key: "recordingUnit-count", kind: "card", order: 30, render: () => <RecordingUnitCountCardWidget {...ctx} /> }];
}
