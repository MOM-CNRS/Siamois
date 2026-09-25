import { EntityCountCard } from "../../components/home/EntityCountCard";
import { useOrganizationCounts } from "../organizationCounts";
import type { HomeWidgetContext, HomeWidgetDef } from "../types";

// homePanel.xhtml's own card for this type — opens the organization-wide React list (no id →
// App's client-side router, no page load).
function PlaceCountCardWidget({ organizationId, onNavigate }: HomeWidgetContext) {
  const { data } = useOrganizationCounts(organizationId);

  return (
    <EntityCountCard
      icon="bi bi-geo-alt"
      label="Lieux"
      description="Les lieux et leurs découpages spatiaux"
      count={data?.places}
      className="sia-welcome-card sia-spatial-unit"
      chipClassName="spatial-unit-count-chip-alt"
      onOpen={() => onNavigate?.("place")}
    />
  );
}

export function placeHomeWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [{ key: "place-count", kind: "card", order: 20, render: () => <PlaceCountCardWidget {...ctx} /> }];
}
