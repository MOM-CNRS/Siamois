import {apiFetch} from "../../api/client";
import {filtersToQueryParams} from "../../panels/list/tableState";
import {getEntityType} from "../registry";
import type {ListParams, PagedResult} from "./types";

// Response envelope shared by every GET .../{collection} list endpoint (ListResponse<T> server
// side) — meta.total, not totalCount, is the wire field name; PagedResult normalizes that for the
// generic panels. Kept generic over T so both a plain list and a scoped one share this shape.
interface ListResponseBody<T> {
  data: T[];
  meta: { total: number; limit: number; offset: number; matchCount?: number };
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
export async function fetchList<T>(
  collectionPath: string,
  params: ListParams,
): Promise<PagedResult<T>> {
  const query = resultSetQuery(params);
  if (params.sort) query.set("sort", params.sort);
  query.set("offset", String(params.offset));
  query.set("limit", String(params.limit));
  if (params.fields) query.set("fields", params.fields);
  if (params.tree) query.set("tree", "true");
  if (params.matchCount) query.set("matchCount", "true");
  if (params.parent != null) query.set("parent", String(params.parent));

  const path = `${basePath(collectionPath, params)}?${query.toString()}`;
  const body = await apiFetch<ListResponseBody<T>>(path);
  return {
    data: body.data,
    totalCount: body.meta.total,
    limit: body.meta.limit,
    offset: body.meta.offset,
    ...(body.meta.matchCount != null
      ? { matchCount: body.meta.matchCount }
      : {}),
  };
}

// The parameters that define a list's result set, without its order, paging or presentation — what
// its map shows. The order is left out on purpose: a map has none, so sorting the list never refetches it.
export type ResultSetParams = Pick<ListParams, "search" | "organizationId" | "filters" | "scope">;

/** The result set of a list's parameters, so that only what changes the rows keys its map. */
export function resultSetOf(params: ResultSetParams): ResultSetParams {
  const { search, organizationId, filters, scope } = params;
  return { search, organizationId, filters, scope };
}

// What the list's map view shows — GET .../{collection}/map (EntityMapResponse server side), the
// same result set as the list answered with each row's geometry in WGS84 instead of a page. The server
// only says what it alone knows; what is left out and how features look is derived client-side.
export interface MapFeature {
  id: string;
  label: string;
  type?: { resolvedLabel?: string | null } | null;
  validationStatus?: string | null;
  geometry: GeoJSON.Geometry;
}

export interface MapData {
  features: MapFeature[];
  // total: rows of the result set; examined: those looked at (up to the server's cap).
  meta: { total: number; examined: number };
}

export function fetchMap(collectionPath: string, params: ResultSetParams): Promise<MapData> {
  return apiFetch<MapData>(`${basePath(collectionPath, params)}/map?${resultSetQuery(params).toString()}`);
}

// One element on a map, outside of any list — GET .../{collection}/{id}/map (EntityMapFeatureResponse):
// the fiche open beside a map whose result set left it out. Null when it has no placeable geometry.
export async function fetchMapFeature(collectionPath: string, id: string | number): Promise<MapFeature | null> {
  const body = await apiFetch<{ feature: MapFeature | null }>(`/api/v1/${collectionPath}/${encodeURIComponent(String(id))}/map`);
  return body.feature;
}

// The query parameters that define a result set — shared by the list and its map.
function resultSetQuery(params: ResultSetParams): URLSearchParams {
  const query = new URLSearchParams();
  if (params.search) query.set("search", params.search);
  if (params.organizationId != null)
    query.set("organizationId", String(params.organizationId));
  if (params.filters) {
    // f.<key>[.from|.to] — ProjectListFilter/RecordingUnitListFilter's own contract;
    // filtersToQueryParams is the one place that owns the encoding, shared with the base64url
    // ?s= state (panels/list/tableState.ts).
    for (const [key, value] of filtersToQueryParams(params.filters).entries()) {
      query.append(key, value);
    }
  }
  return query;
}

function basePath(collectionPath: string, params: Pick<ListParams, "scope">): string {
  const { scope } = params;
  if (!scope) return `/api/v1/${collectionPath}`;

  const parentConfig = getEntityType(scope.entityType);
  const parentCollection = parentConfig?.collectionPath ?? scope.entityType;
  const childSegment = scope.path ?? collectionPath;
  return `/api/v1/${parentCollection}/${scope.id}/${childSegment}`;
}
