// Mirrors fr.siamois.ui.api.openapi.v1.resource.study.StudyResource — GET /api/v1/projects/{id}/studies
// (list), GET /api/v1/studies?organizationId=… (organization list) and GET /api/v1/studies/{id}
// (detail) all return this shape. Keep in sync with that class if it changes.

import type { OrganizationIdentifier, ResolvedConcept, ProjectRef } from "../common";

export interface StudyPermissions {
  canEdit: boolean;
  canDelete: boolean;
  // Validator right — omitted by the server when false.
  canValidate?: boolean;
}

export interface StudyResource {
  resourceType: string;
  id: string;
  identifier?: string | null;
  projectId?: string | null;
  // Organization-wide list only — see ProjectRef.
  project?: ProjectRef | null;
  organization?: OrganizationIdentifier | null;
  // The study's type: "Etude de mobilier", "Etude d'UE" or "Traitement" by default.
  type?: ResolvedConcept | null;
  // Raw values, list and detail alike (see StudyAnswersProjector); the links to recording units and
  // finds are references.
  answers?: Record<string, unknown>;
  _permissions?: StudyPermissions;
  resourceUri?: string | null;
  bookmarked?: boolean;
  // TraceableEntity.validationStatus — "en cours", "terminé", "validé", "annulé".
  validationStatus?: "INCOMPLETE" | "COMPLETE" | "VALIDATED" | "CANCELLED" | null;
}

// Same resource both in the list and the detail response, like Container/Phase/Find.
export type StudySummary = StudyResource;
export type StudyDetail = StudyResource;
