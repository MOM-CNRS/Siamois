import { EntityCountCard } from "../components/home/EntityCountCard";
import { useOrganizationCounts, type OrganizationCounts } from "./organizationCounts";
import type { HomeWidgetContext, HomeWidgetDef } from "./types";

export interface CountCardSpec {
  entityType: string;
  count: keyof OrganizationCounts;
  icon: string;
  label: string;
  description: string;
  // homePanel.xhtml's card and count chip classes, and the card's place among the others.
  className: string;
  chipClassName: string;
  order: number;
}

function CountCard({ spec, organizationId, onNavigate }: HomeWidgetContext & { spec: CountCardSpec }) {
  const { data } = useOrganizationCounts(organizationId);
  return (
    <EntityCountCard
      icon={spec.icon}
      label={spec.label}
      description={spec.description}
      count={data?.[spec.count]}
      className={spec.className}
      chipClassName={spec.chipClassName}
      onOpen={() => onNavigate?.(spec.entityType)}
    />
  );
}

/**
 * The home "database access" card of an entity type: its organization-wide count, opening its
 * organization-wide list (no id → App's client-side router, no page load).
 */
export function countCardWidgets(spec: CountCardSpec): (ctx: HomeWidgetContext) => HomeWidgetDef[] {
  return (ctx) => [
    { key: `${spec.entityType}-count`, kind: "card", order: spec.order, render: () => <CountCard spec={spec} {...ctx} /> },
  ];
}
