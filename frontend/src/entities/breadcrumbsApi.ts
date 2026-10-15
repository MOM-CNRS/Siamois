import { apiFetch } from "../api/client";
import type { EntityBreadcrumbs } from "./types";

interface BreadcrumbsResponseBody {
  data: EntityBreadcrumbs;
}

// GET /api/v1/{collection}/{id}/breadcrumbs — every path from the fiche's project down to it
// (EntityBreadcrumbsService server side); the first path is the default one.
export async function fetchBreadcrumbs(collectionPath: string, id: string | number): Promise<EntityBreadcrumbs> {
  const body = await apiFetch<BreadcrumbsResponseBody>(`/api/v1/${collectionPath}/${id}/breadcrumbs`);
  return { paths: body.data.paths ?? [], truncated: body.data.truncated ?? false };
}
