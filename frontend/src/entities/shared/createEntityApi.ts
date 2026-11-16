import {apiFetch} from "../../api/client";
import type {AnswerInputBody} from "../../fields/types";
import {fetchList} from "./listApi";
import type {ListParams, PagedResult} from "./types";

interface DataBody<T> {
  data: T;
}

/**
 * The REST adapter of an entity served as `/api/v1/<collectionPath>` with the common contract:
 * a paged list, a detail (`getQuery`: extra query string, e.g. the counts its tabs need), a PATCH
 * of answers keyed by field id, a creation and a copy — each answering `{ data: <detail> }`.
 * `toCreateBody` shapes the creation request when it isn't the form's body as is.
 */
export function createEntityApi<TSummary, TDetail, TCreate>(
  collectionPath: string,
  options: { getQuery?: string; toCreateBody?: (body: TCreate) => unknown } = {},
) {
  const base = `/api/v1/${collectionPath}`;
  return {
    list: (params: ListParams): Promise<PagedResult<TSummary>> => fetchList<TSummary>(collectionPath, params),

    get: async (id: string | number): Promise<TDetail> =>
      (await apiFetch<DataBody<TDetail>>(`${base}/${id}${options.getQuery ? `?${options.getQuery}` : ""}`)).data,

    patchAnswers: async (id: string | number, answers: Record<string, AnswerInputBody>): Promise<TDetail> =>
      (await apiFetch<DataBody<TDetail>>(`${base}/${id}`, { method: "PATCH", body: { answers } })).data,

    // The header's save: a new identifier (the server checks it is unique in the project) and/or
    // answers, in one request.
    patch: async (
      id: string | number,
      body: { identifier?: string; answers?: Record<string, AnswerInputBody> },
    ): Promise<TDetail> => (await apiFetch<DataBody<TDetail>>(`${base}/${id}`, { method: "PATCH", body })).data,

    // The entity's geometry, drawn on a map: sent in WGS84 (SRID 4326), stored as is; null removes it.
    patchGeometry: async (id: string | number, geometry: GeoJSON.Geometry | null): Promise<TDetail> =>
      (await apiFetch<DataBody<TDetail>>(`${base}/${id}`, { method: "PATCH", body: { geom: wgs84GeometryBody(geometry) } })).data,

    create: async (body: TCreate): Promise<TDetail> =>
      (await apiFetch<DataBody<TDetail>>(base, { method: "POST", body: options.toCreateBody ? options.toCreateBody(body) : body })).data,

    duplicate: async (id: string | number): Promise<TDetail> =>
      (await apiFetch<DataBody<TDetail>>(`${base}/${id}/duplicate`, { method: "POST" })).data,
  };
}

// What a PATCH's `geom` holds (GeometryDTO server side): the GeoJSON geometry and the SRID it is in.
export const WGS84_SRID = 4326;

export function wgs84GeometryBody(geometry: GeoJSON.Geometry | null): (GeoJSON.Geometry & { srid: number }) | null {
  return geometry ? { ...geometry, srid: WGS84_SRID } : null;
}
