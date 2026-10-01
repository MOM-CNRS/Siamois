import type { EntitySibling, EntitySiblings, EntityTypeConfig, PagedResult } from "../entities/types";
import { entityRowLabel } from "../fields/optionSources";
import type { ListContext } from "./listContext";
import { queryKeys } from "../api/queryKeys";

type Row = { id?: string | number | null } & Record<string, unknown>;

/**
 * The previous and next entity of `id` in the list it was opened from: the rows at position ±1 of
 * the same request the table made, so the arrows follow its sort, filters, search and scope exactly.
 * They loop at the ends (the first's previous is the last, the last's next is the first), as the
 * default-order arrows always did.
 *
 * Single-row requests, on purpose: the list endpoints only take an offset that is a multiple of the
 * page size, so an arbitrary window (index-1, size 3) is a 400 — one row at any offset is always valid.
 *
 * `null` when the neighbours can't be trusted — the row at the remembered position isn't `id` any
 * more (the list changed, or this entity was edited out of its place) or a request failed: the
 * caller then falls back to the entity's default order rather than walking a stale list.
 */
export async function fetchListSiblings(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- same as the registry's configs (entities/registry.ts)
  config: EntityTypeConfig<any, any>,
  id: string | number,
  context: ListContext,
): Promise<EntitySiblings | null> {
  const rowAt = (offset: number) =>
    (config.api.list({ ...context.params, offset, limit: 1 }) as Promise<PagedResult<Row>>);

  try {
    // The current position first: it checks the memory still holds, and gives the list's size.
    const current = await rowAt(context.index);
    const total = current.totalCount;
    if (current.data[0]?.id == null || String(current.data[0].id) !== String(id)) return null;
    if (total < 2) return { previous: undefined, next: undefined };

    const previousIndex = context.index > 0 ? context.index - 1 : total - 1;
    const nextIndex = context.index < total - 1 ? context.index + 1 : 0;
    const [previous, next] = await Promise.all([rowAt(previousIndex), rowAt(nextIndex)]);

    const sibling = (row: Row | undefined, index: number): EntitySibling | undefined =>
      row?.id == null
        ? undefined
        : {
            id: row.id,
            label: entityRowLabel(row as Parameters<typeof entityRowLabel>[0]),
            resourceUri: config.routes.detail(row.id),
            index,
          };
    return { previous: sibling(previous.data[0], previousIndex), next: sibling(next.data[0], nextIndex) };
  } catch {
    return null;
  }
}

/**
 * The arrows' query for `id`: the neighbours in the list it was opened from when there is one that
 * still holds, the entity's default order (`config.api.siblings`) otherwise. One definition for the
 * query and for the hover prefetch, so both land on the same cache entry.
 */
export function siblingsQuery(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- same as the registry's configs (entities/registry.ts)
  config: EntityTypeConfig<any, any>,
  entityType: string,
  id: string | number,
  organizationId: number | undefined,
  context: ListContext | undefined,
) {
  return {
    queryKey: queryKeys.entitySiblings(entityType, id, organizationId, context),
    queryFn: async (): Promise<EntitySiblings> =>
      (context ? await fetchListSiblings(config, id, context) : null) ?? config.api.siblings!(id, { organizationId }),
  };
}
