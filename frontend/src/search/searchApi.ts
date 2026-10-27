import {apiFetch} from "../api/client";
import type {SearchFilter} from "./queryParser";

export interface SearchItem {
  id: number;
  label: string;
  subtitle?: string | null;
  projectId?: number | null;
  // Name of that project, for the chip; absent for a project or a place.
  projectName?: string | null;
}

export interface SearchSection {
  // A registry key: project, recordingUnit, find, place.
  type: string;
  total: number;
  items: SearchItem[];
}

export interface SearchParams {
  organizationId: number;
  q: string;
  projectId?: string;
  filters: SearchFilter[];
  // Entity types to search (registry keys); every type when absent.
  types?: string[];
  limit?: number;
}

// GET /api/v1/search — a few matches per entity type, and each type's total.
export async function fetchGlobalSearch(params: SearchParams, signal?: AbortSignal): Promise<SearchSection[]> {
  const query = new URLSearchParams({ organizationId: String(params.organizationId), q: params.q });
  if (params.projectId) query.set("projectId", params.projectId);
  for (const filter of params.filters) query.append("filters", `${filter.key}:${filter.value}`);
  for (const type of params.types ?? []) query.append("types", type);
  if (params.limit) query.set("limit", String(params.limit));
  const body = await apiFetch<{ sections: SearchSection[] }>(`/api/v1/search?${query.toString()}`, { signal });
  return body.sections ?? [];
}
