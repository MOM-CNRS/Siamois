import {useQuery} from "@tanstack/react-query";
import {getEntityType} from "../../entities/registry";
import type {FilterValue, ListScope} from "../../entities/shared/types";

/** A tab's count, as the light grey pill after its title. */
export function TabCount({ value }: { value: number | string }) {
  return <span className="sia-tab-count">{value}</span>;
}

/**
 * The count of a list tab the user configured: what its own request returns — the target's list under the fiche, with
 * the tab's fixed filters. A key under "entity-list" so a create or a delete refreshes it too.
 */
export function ListTabCount({
  target,
  scope,
  filters,
  organizationId,
}: {
  target: string;
  scope: ListScope;
  filters?: Record<string, FilterValue>;
  organizationId?: number;
}) {
  const config = getEntityType(target);
  const { data } = useQuery({
    queryKey: ["entity-list", target, "tab-count", organizationId, scope.entityType, scope.id, scope.projectId ?? null, filters ?? null],
    queryFn: () => config!.api.list({ offset: 0, limit: 1, organizationId, scope, filters }),
    select: (result) => result.totalCount,
    enabled: config != null,
  });
  return data != null ? <TabCount value={data} /> : null;
}
