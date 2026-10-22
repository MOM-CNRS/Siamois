import { apiFetch } from "../api/client";
import type { EntityBreadcrumbs } from "./types";

interface BreadcrumbsResponseBody {
  data: EntityBreadcrumbs;
}

// GET /api/v1/{collection}/{id}/breadcrumbs — every path from the fiche's project down to it
// (EntityBreadcrumbsService server side); the first path is the default one. `via` is the fiche the
// user came from: the paths going through it come first.
export async function fetchBreadcrumbs(
  collectionPath: string,
  id: string | number,
  via?: { entityType: string; id: string | number },
): Promise<EntityBreadcrumbs> {
  const query = via ? `?via=${encodeURIComponent(`${via.entityType}:${via.id}`)}` : "";
  const body = await apiFetch<BreadcrumbsResponseBody>(`/api/v1/${collectionPath}/${id}/breadcrumbs${query}`);
  return { paths: body.data.paths ?? [], truncated: body.data.truncated ?? false };
}
