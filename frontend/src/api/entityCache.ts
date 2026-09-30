import type { QueryClient } from "@tanstack/react-query";
import type { PagedResult } from "../entities/types";
import { queryKeys } from "./queryKeys";

/**
 * Writes a known change of one entity into every cached copy of it — the rows of its type's lists
 * and its detail — instead of refetching them. Ids are compared as strings: a detail opened from a
 * URL is keyed by a string id, a row carries the server's number.
 */
export function patchCachedEntity(
  queryClient: QueryClient,
  entityType: string,
  entityId: string | number,
  patch: Record<string, unknown>,
): void {
  const isThis = (id: unknown) => String(id) === String(entityId);
  queryClient.setQueriesData<PagedResult<{ id?: unknown }>>({ queryKey: queryKeys.entityList(entityType) }, (page) =>
    page?.data?.some((row) => isThis(row.id))
      ? { ...page, data: page.data.map((row) => (isThis(row.id) ? { ...row, ...patch } : row)) }
      : page,
  );
  queryClient.setQueriesData<object>(
    { queryKey: queryKeys.entityDetailsOf(entityType), predicate: ({ queryKey }) => isThis(queryKey[2]) },
    (entity) => entity && { ...entity, ...patch },
  );
}
