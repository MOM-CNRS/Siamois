import { EntityCountCard } from "../../components/home/EntityCountCard";
import { useOrganizationCounts } from "../organizationCounts";
import type { HomeWidgetContext, HomeWidgetDef } from "../types";

// homePanel.xhtml's own card for this type — opens the organization-wide React list (no id →
// App's client-side router, no page load).
function ContainerCountCardWidget({ organizationId, onNavigate }: HomeWidgetContext) {
  const { data } = useOrganizationCounts(organizationId);

  return (
    <EntityCountCard
      icon="bi bi-box-seam"
      label="Contenants"
      description="Contenants mobile (boites, sacs, etc.) pour le stockage des mobiliers et documents"
      count={data?.containers}
      className="sia-welcome-card sia-container"
      chipClassName="container-count-chip-alt"
      onOpen={() => onNavigate?.("container")}
    />
  );
}

export function containerHomeWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [{ key: "container-count", kind: "card", order: 60, render: () => <ContainerCountCardWidget {...ctx} /> }];
}
