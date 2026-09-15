import { apiFetch } from "./client";
import type { ResourceRef } from "../form/schema";
import type { ResolvedConceptResource } from "./recordingUnit";

/**
 * Autocomplete data sources for SELECT_* form fields. Only the project-scoped endpoints are wired here
 * (concepts, phases, containers, specimens) — they resolve the owning institution server-side from the
 * projectId alone. Institution-scoped pickers (spatial unit, person, cross recording-unit/action-unit
 * references) need an organizationId this component doesn't have on hand (RecordingUnitResource only
 * exposes `projectId`, not the institution id) — left unwired rather than sending a wrong id; see
 * fieldRegistry.tsx's `UnsupportedField` for those answerTypes.
 */

interface ProjectConceptsResponse {
  data: ResolvedConceptResource[];
}

export async function searchConcepts(projectId: string, fieldCode: string, q: string): Promise<ResourceRef[]> {
  const params = new URLSearchParams({ fieldCode, q });
  const response = await apiFetch<ProjectConceptsResponse>(`/projects/${projectId}/concepts?${params}`);
  return response.data.map((c) => ({ resourceId: c.id, resourceType: "concepts", label: c.resolvedLabel }));
}

interface PhaseResource {
  resourceType: "phases";
  id: string;
  identifier: string;
  title: string;
  label: string;
}
interface PhaseListResponse {
  data: PhaseResource[];
}

export async function searchPhases(projectId: string, q: string): Promise<ResourceRef[]> {
  const params = new URLSearchParams(q ? { q } : {});
  const response = await apiFetch<PhaseListResponse>(`/projects/${projectId}/phases?${params}`);
  return response.data.map((p) => ({ resourceId: p.id, resourceType: "phases", label: p.label }));
}

interface ContainerResource {
  resourceType: "containers";
  id: string;
  identifier: string;
  label: string;
}
interface ContainerListResponse {
  data: ContainerResource[];
}

export async function searchContainers(projectId: string, q: string): Promise<ResourceRef[]> {
  const params = new URLSearchParams(q ? { q } : {});
  const response = await apiFetch<ContainerListResponse>(`/projects/${projectId}/containers?${params}`);
  return response.data.map((c) => ({ resourceId: c.id, resourceType: "containers", label: c.label }));
}

interface FindResource {
  resourceType: "finds";
  id: string;
  fullIdentifier: string;
}
interface FindListResponse {
  data: FindResource[];
}

export async function searchSpecimens(projectId: string, q: string): Promise<ResourceRef[]> {
  const params = new URLSearchParams(q ? { q } : {});
  const response = await apiFetch<FindListResponse>(`/projects/${projectId}/mobiliers?${params}`);
  return response.data.map((f) => ({ resourceId: f.id, resourceType: "finds", label: f.fullIdentifier }));
}
