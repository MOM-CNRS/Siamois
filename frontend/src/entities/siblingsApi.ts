import { apiFetch } from "../api/client";
import type { EntitySiblings } from "./types";

interface SiblingsResponseBody {
  data: { previous?: EntitySiblings["previous"] | null; next?: EntitySiblings["next"] | null };
}

// GET /api/v1/{collection}/{id}/siblings — the generic previous/next (EntitySiblingsService server
// side: same project for project-owned kinds, same organization for places, creation order).
// Project keeps its own getProjectSiblings, whose endpoint also takes the list's organization.
export async function fetchSiblings(collectionPath: string, id: string | number): Promise<EntitySiblings> {
  const body = await apiFetch<SiblingsResponseBody>(`/api/v1/${collectionPath}/${id}/siblings`);
  return {
    previous: body.data.previous ?? undefined,
    next: body.data.next ?? undefined,
  };
}
