// Mirrors fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource — GET
// /api/v1/projects/{id}/recording-units (list) and GET /api/v1/recording-units/{id} (detail) both
// return this shape, like ProjectResource. Keep in sync with that class if it changes.

import type { OrganizationIdentifier, ResolvedConcept } from "../project/types";

export interface RecordingUnitCounts {
  children?: number | null;
  finds?: number | null;
  parents?: number | null;
  documents?: number | null;
  relationships?: number | null;
}

export interface RecordingUnitPermissions {
  canEdit: boolean;
  canDelete: boolean;
}

export interface RecordingUnitResource {
  resourceType: string;
  id: string;
  syncRevision?: number | null;
  identifier?: string | null;
  fullIdentifier: string;
  projectId?: string | null;
  // The RU's owning institution — RecordingUnitResource carries this (unlike ProjectResource,
  // which is what its own list/fiche is scoped by) specifically so the React fiche can resolve
  // concept autocomplete (GET /api/v1/organizations/{id}/concepts) without a second round-trip
  // to fetch the parent project just to read ITS organization.
  organization?: OrganizationIdentifier | null;
  type?: ResolvedConcept | null;
  geom?: unknown;
  // List rows carry RAW values (RecordingUnitAnswersProjector) — same convention as
  // ProjectResource.answers. The detail endpoint instead wraps each entry in a FieldAnswer
  // envelope; fields/types.ts's unwrapAnswer already handles both shapes, so this stays untyped.
  answers?: Record<string, unknown>;
  _counts?: RecordingUnitCounts;
  _permissions?: RecordingUnitPermissions;
}

// Same resource both in the list and the detail response, like Project.
export type RecordingUnitSummary = RecordingUnitResource;
export type RecordingUnitDetail = RecordingUnitResource;
