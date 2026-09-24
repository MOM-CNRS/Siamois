// Mirrors fr.siamois.ui.api.openapi.v1.resource.find.FindResource — GET
// /api/v1/projects/{id}/mobiliers (list) and GET /api/v1/finds/{id} (detail) both return this
// shape, like RecordingUnitResource. Keep in sync with that class if it changes.

import type { OrganizationIdentifier, ResolvedConcept, ProjectRef } from "../project/types";

export interface FindPermissions {
  canEdit: boolean;
  canDelete: boolean;
}

export interface RecordingUnitRef {
  resourceType: string;
  id: string;
  // RecordingUnitReference.fullIdentifier — the label of the find's UE column.
  fullIdentifier?: string | null;
}

export interface FindResource {
  resourceType: string;
  id: string;
  fullIdentifier: string;
  collectionDate?: string | null;
  projectId?: string | null;
  // Organization-wide list only — see ProjectRef.
  project?: ProjectRef | null;
  type?: ResolvedConcept | null;
  recordingUnit?: RecordingUnitRef | null;
  organization?: OrganizationIdentifier | null;
  geom?: unknown;
  // List rows would carry raw values; the detail endpoint wraps each entry in a FieldAnswer
  // envelope (buildFindMobilierForm) — same convention as RecordingUnitResource.answers.
  // fields/types.ts's unwrapAnswer already handles both shapes, so this stays untyped.
  answers?: Record<string, unknown>;
  _permissions?: FindPermissions;
  resourceUri?: string | null;
}

// Same resource both in the list and the detail response, like Project/RecordingUnit.
export type FindSummary = FindResource;
export type FindDetail = FindResource;
