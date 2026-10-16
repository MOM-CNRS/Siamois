// Mirrors fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource — GET
// /api/v1/projects/{id}/documents?limit=… (list), GET /api/v1/documents?organizationId=… (organization
// list) and GET /api/v1/documents/{id} (detail) all return this shape. The first block is what the mobile
// API has always served; keep both in sync with that class if it changes.

import type { OrganizationIdentifier, ResolvedConcept, ProjectRef } from "../common";

export interface DocumentPermissions {
  canEdit: boolean;
  canDelete: boolean;
  // Validator right — omitted by the server when false.
  canValidate?: boolean;
}

export interface DocumentResource {
  resourceType: string;
  id: string;
  identifier?: string | null;
  title?: string | null;
  description?: string | null;
  label?: string | null;
  // The stored file, when there is one.
  fileName?: string | null;
  mimeType?: string | null;
  url?: string | null;
  fileCode?: string | null;
  size?: number | null;
  md5Sum?: string | null;
  projectId?: string | null;
  // Organization-wide list only — see ProjectRef.
  project?: ProjectRef | null;
  // The category: the type of the configurable table.
  type?: ResolvedConcept | null;
  organization?: OrganizationIdentifier | null;
  // Raw values, list and detail alike (see DocumentAnswersProjector).
  answers?: Record<string, unknown>;
  _permissions?: DocumentPermissions;
  resourceUri?: string | null;
  bookmarked?: boolean;
  // TraceableEntity.validationStatus — "en cours", "terminé", "validé", "annulé".
  validationStatus?: "INCOMPLETE" | "COMPLETE" | "VALIDATED" | "CANCELLED" | null;
}

// Same resource both in the list and the detail response, like Find/RecordingUnit/Phase.
export type DocumentSummary = DocumentResource;
export type DocumentDetail = DocumentResource;
