// Mirrors fr.siamois.ui.api.openapi.v1.resource.container.ContainerResource — GET
// /api/v1/projects/{id}/containers (list) and GET /api/v1/containers/{id} (detail) both return
// this shape, like PhaseResource. Keep in sync with that class if it changes.

import type { OrganizationIdentifier, ResolvedConcept, ProjectRef } from "../common";

export interface ContainerPermissions {
  canEdit: boolean;
  canDelete: boolean;
  // Validator right — omitted by the server when false.
  canValidate?: boolean;
}

export interface ContainerResource {
  resourceType: string;
  id: string;
  identifier?: string | null;
  projectId?: string | null;
  // Organization-wide list only — see ProjectRef.
  project?: ProjectRef | null;
  organization?: OrganizationIdentifier | null;
  type?: ResolvedConcept | null;
  // Same convention as PhaseResource.answers — always raw values (list AND detail), Container
  // has no CustomFieldAnswer at all (see ContainerAnswersProjector's own javadoc).
  answers?: Record<string, unknown>;
  _permissions?: ContainerPermissions;
  resourceUri?: string | null;
  bookmarked?: boolean;
  // Detail only: what the fiche's tab badges count.
  _counts?: { documents?: number | null };
  // TraceableEntity.validationStatus — "en cours", "terminé", "validé", "annulé".
  validationStatus?: "INCOMPLETE" | "COMPLETE" | "VALIDATED" | "CANCELLED" | null;
}

// Same resource both in the list and the detail response, like Phase/Find/RecordingUnit.
export type ContainerSummary = ContainerResource;
export type ContainerDetail = ContainerResource;
