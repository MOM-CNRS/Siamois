import { apiFetch } from "../api/client";
import { filtersToQueryParams } from "../panels/tableState";
import { getEntityType } from "./registry";
import type { ListParams, PagedResult } from "./types";

// Response envelope shared by every GET .../{collection} list endpoint (ListResponse<T> server
// side) — meta.total, not totalCount, is the wire field name; PagedResult normalizes that for the
// generic panels. Kept generic over T so both a plain list and a scoped one share this shape.
interface ListResponseBody<T> {
  data: T[];
  meta: { total: number; limit: number; offset: number };
}

/**
 * The one query-building/normalizing path every entity's own list() function goes through (plan
 * §8 "generic related list" follow-up) — previously duplicated per entity (entities/project/api.ts
 * was the only one, but a second copy is exactly how that duplication would have started).
 *
 * `collectionPath` is the entity's own REST segment (EntityTypeConfig.collectionPath) for the
 * unscoped case. When `params.scope` is set, the URL is instead built off the PARENT's own
 * collectionPath — `/api/v1/{parentCollection}/{scope.id}/{scope.path ?? collectionPath}` — so a
 * relation ("the recording units of project 5") never needs its own registry entry or a
 * per-relation lookup table; only the parent's own EntityTypeConfig.collectionPath is consulted,
 * exactly as `getEntityType(scope.entityType)` already resolves every other cross-entity concern
 * (routes, icon, labels) elsewhere in this codebase.
 */
export async function fetchList<T>(collectionPath: string, params: ListParams): Promise<PagedResult<T>> {
  const query = new URLSearchParams();
  query.set("offset", String(params.offset));
  query.set("limit", String(params.limit));
  if (params.search) query.set("search", params.search);
  if (params.sort) query.set("sort", params.sort);
  if (params.organizationId != null) query.set("organizationId", String(params.organizationId));
  if (params.fields) query.set("fields", params.fields);
  if (params.filters) {
    // f.<key>[.from|.to] — ProjectListFilter/RecordingUnitListFilter's own contract;
    // filtersToQueryParams is the one place that owns the encoding, shared with the base64url
    // ?s= state (panels/tableState.ts).
    for (const [key, value] of filtersToQueryParams(params.filters).entries()) {
      query.append(key, value);
    }
  }

  const path = `${basePath(collectionPath, params)}?${query.toString()}`;
  const body = await apiFetch<ListResponseBody<T>>(path);
  return {
    data: body.data,
    totalCount: body.meta.total,
    limit: body.meta.limit,
    offset: body.meta.offset,
  };
}

function basePath(collectionPath: string, params: ListParams): string {
  const { scope } = params;
  if (!scope) return `/api/v1/${collectionPath}`;

  const parentConfig = getEntityType(scope.entityType);
  const parentCollection = parentConfig?.collectionPath ?? scope.entityType;
  const childSegment = scope.path ?? collectionPath;
  return `/api/v1/${parentCollection}/${scope.id}/${childSegment}`;
}
