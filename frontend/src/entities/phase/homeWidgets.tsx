import { EntityCountCard } from "../../components/home/EntityCountCard";
import { useOrganizationCounts } from "../organizationCounts";
import type { HomeWidgetContext, HomeWidgetDef } from "../types";

// homePanel.xhtml's own card for this type — opens the organization-wide React list (no id →
// App's client-side router, no page load).
function PhaseCountCardWidget({ organizationId, onNavigate }: HomeWidgetContext) {
  const { data } = useOrganizationCounts(organizationId);

  return (
    <EntityCountCard
      icon="bi bi-layers"
      label="Phases"
      description="Phases et sous-phases chronologiques"
      count={data?.phases}
      className="sia-welcome-card sia-recording-unit"
      chipClassName="recording-unit-count-chip-alt"
      onOpen={() => onNavigate?.("phase")}
    />
  );
}

export function phaseHomeWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [{ key: "phase-count", kind: "card", order: 50, render: () => <PhaseCountCardWidget {...ctx} /> }];
}
