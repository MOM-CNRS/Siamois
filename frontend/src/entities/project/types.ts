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
  // Only present on the detail response (GET /api/v1/projects/{id}) — the list never computes
  // it, see ProjectResponseMapper's own javadoc on the equivalent server-side field.
  finds?: number | null;
  phases?: number | null;
  containers?: number | null;
}

export interface ProjectPermissions {
  canEdit: boolean;
  canDelete: boolean;
  // PROJECT_MANAGE_SETTINGS — omitted by the server when false.
  canManageSettings?: boolean;
  // Validator right (PROJECT_VALIDATE…) — omitted by the server when false.
  canValidate?: boolean;
}

// Mirrors fr.siamois.domain.models.ValidationStatus — the merged "statut + identifiant" column's
// left-hand badge (JSF: /panel/header/validationButton.xhtml). Read-only on the API: toggling it
// is still a JSF-side action (panelModel.toggleValidate()).
export type ValidationStatus = "INCOMPLETE" | "COMPLETE" | "VALIDATED" | "CANCELLED";

export interface ProjectResource {
  resourceType: string;
  id: string;
  name: string;
  fullIdentifier: string;
  identifier: string;
  beginDate?: string | null;
  endDate?: string | null;
  validated?: ValidationStatus | null;
  type?: ResolvedConcept | null;
  mainLocation?: PlaceLight | null;
  spatialContext?: PlaceLight[];
  organization?: OrganizationIdentifier | null;
  _counts?: ProjectCounts;
  _permissions?: ProjectPermissions;
  bookmarked?: boolean;
  // JSF's own navigation/bookmark URI for this project ("/action-unit/{id}"), served by the API so
  // the prefix isn't hardcoded client-side — ActionUnitPanel.ressourceUri() is the source of truth.
  resourceUri?: string;
  // Present only when the request asked for a projection (GET /api/v1/projects?fields=…). Keyed by
  // field id, holding RAW values (no FieldAnswer envelope): scalar for TEXT/INTEGER/DECIMAL/DATETIME,
  // ResourceRef for SELECT_ONE_*, ResourceRef[] for SELECT_MULTIPLE_*. Field metadata (label,
  // answerType, binding) comes from the catalog at GET /api/v1/organizations/{id}/project-types,
  // never from here. Read it through resolveValueBinding, not directly.
  answers?: Record<string, unknown>;
}

// Mirrors fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef — the shape a SELECT_* answer takes
// on the wire.
export interface ResourceRef {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

// Same resource both in the list and the detail response — distinct aliases kept for the
// EntityTypeConfig<TSummary, TDetail> slots so a future entity with genuinely different
// summary/detail shapes isn't awkward to compare this config against.
export type ProjectSummary = ProjectResource;
export type ProjectDetail = ProjectResource;

// A row's project on an organization-wide list (ResourceRef server side, set by
// OrganizationListsControllerApi only) — absent on project-scoped lists and on details.
export interface ProjectRef {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}
