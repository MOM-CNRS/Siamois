// Mirrors fr.siamois.ui.api.openapi.v1.resource.phase.PhaseResource — GET
// /api/v1/projects/{id}/phases (list) and GET /api/v1/phases/{id} (detail) both return this
// shape, like FindResource. Keep in sync with that class if it changes.

import type { OrganizationIdentifier, ResolvedConcept, ProjectRef } from "../project/types";

export interface PhasePermissions {
  canEdit: boolean;
  canDelete: boolean;
  // Validator right — omitted by the server when false.
  canValidate?: boolean;
}

export interface PhaseResource {
  resourceType: string;
  id: string;
  identifier?: string | null;
  title?: string | null;
  label?: string | null;
  projectId?: string | null;
  // Organization-wide list only — see ProjectRef.
  project?: ProjectRef | null;
  type?: ResolvedConcept | null;
  organization?: OrganizationIdentifier | null;
  // Same convention as FindResource/RecordingUnitResource.answers, but always raw values (list
  // AND detail alike) — see PhaseAnswersProjector's own javadoc for why Phase has no separate
  // FieldAnswer-enveloped shape at all.
  answers?: Record<string, unknown>;
  _permissions?: PhasePermissions;
  resourceUri?: string | null;
  bookmarked?: boolean;
  // TraceableEntity.validated — "en cours", "terminé", "validé", "annulé".
  validated?: "INCOMPLETE" | "COMPLETE" | "VALIDATED" | "CANCELLED" | null;
}

// Same resource both in the list and the detail response, like Find/RecordingUnit.
export type PhaseSummary = PhaseResource;
export type PhaseDetail = PhaseResource;
