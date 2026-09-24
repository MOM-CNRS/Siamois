import { EntityCountCard } from "../../components/home/EntityCountCard";
import { useOrganizationCounts } from "../organizationCounts";
import type { HomeWidgetContext, HomeWidgetDef } from "../types";

// homePanel.xhtml's own card for this type — opens the organization-wide React list (no id →
// App's client-side router, no page load).
function FindCountCardWidget({ organizationId, onNavigate }: HomeWidgetContext) {
  const { data } = useOrganizationCounts(organizationId);

  return (
    <EntityCountCard
      icon="bi bi-bucket"
      label="Mobilier"
      description="Mobiliers, prélèvements et VAB"
      count={data?.finds}
      className="sia-welcome-card sia-specimen"
      chipClassName="specimen-count-chip-alt"
      onOpen={() => onNavigate?.("find")}
    />
  );
}

export function findHomeWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [{ key: "find-count", kind: "card", order: 40, render: () => <FindCountCardWidget {...ctx} /> }];
}
