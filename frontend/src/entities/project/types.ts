// Mirrors fr.siamois.ui.api.openapi.v1.resource.project.ProjectResource exactly — GET
// /api/v1/projects and GET /api/v1/projects/{id} both return this shape (list rows and the
// detail entity are the same resource, unlike some entities with separate summary/detail DTOs).
// Keep in sync with that class if it changes.

export interface ResolvedConcept {
  resourceType: string;
  id: string;
  externalUrl?: string | null;
  resolvedLabel?: string | null;
}

export interface PlaceLight {
  resourceType: string;
  id: string;
  name?: string | null;
}

export interface OrganizationIdentifier {
  resourceType: string;
  id: string;
}

export interface ProjectCounts {
  children: number;
  recordingUnits: number;
}

export interface ProjectPermissions {
  canEdit: boolean;
  canDelete: boolean;
}

export interface ProjectResource {
  resourceType: string;
  id: string;
  name: string;
  fullIdentifier: string;
  identifier: string;
  beginDate?: string | null;
  endDate?: string | null;
  type?: ResolvedConcept | null;
  mainLocation?: PlaceLight | null;
  spatialContext?: PlaceLight[];
  organization?: OrganizationIdentifier | null;
  _counts?: ProjectCounts;
  _permissions?: ProjectPermissions;
  bookmarked?: boolean;
}

// Same resource both in the list and the detail response — distinct aliases kept for the
// EntityTypeConfig<TSummary, TDetail> slots so a future entity with genuinely different
// summary/detail shapes isn't awkward to compare this config against.
export type ProjectSummary = ProjectResource;
export type ProjectDetail = ProjectResource;
