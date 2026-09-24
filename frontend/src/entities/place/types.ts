// Mirrors fr.siamois.ui.api.openapi.v1.resource.place.PlaceResource — GET
// /api/v1/places?organizationId=… (list: no answers/formBundle/fields)
// and GET /api/v1/places/{id} (detail, all of it) both return this shape. Keep in sync with that
// class if it changes.

import type { FieldResource } from "../../fields/types";
import type { OrganizationIdentifier, ResolvedConcept } from "../project/types";

export interface GeometryDTO {
  type?: string | null;
  coordinates?: unknown;
  srid?: number | null;
}

export interface PlacePermissions {
  canEdit: boolean;
  canDelete: boolean;
}

export interface FormBundle {
  resourceType: string;
  layoutJson: string;
}

export interface PlaceResource {
  resourceType: string;
  id: string;
  name?: string | null;
  placeNumber?: number | null;
  type?: ResolvedConcept | null;
  organization?: OrganizationIdentifier | null;
  geom?: GeometryDTO | null;
  // Detail only — see PlaceOpenApiService#getPlaceById's own javadoc: raw values (no
  // CustomFieldAnswer for a Place, same convention as Phase/Container), and the address field
  // (CustomFieldSelectOneAddress) is deliberately absent — FullAddress has no ResourceRef
  // equivalent and nothing edits it client-side yet.
  answers?: Record<string, unknown>;
  // Detail only — SpatialUnit.DETAILS_FORM is static (SpatialUnit has no ConfigurableTable entry,
  // so there is no per-type catalog to resolve, unlike Phase/Container/RecordingUnit/Find).
  formBundle?: FormBundle | null;
  fields?: Record<string, FieldResource> | null;
  _permissions?: PlacePermissions;
  resourceUri?: string | null;
  bookmarked?: boolean;
  // Detail only — the fiche's tab badges (PlaceOpenApiService#getPlaceById).
  _counts?: { children?: number | null; projects?: number | null; recordingUnits?: number | null };
}

// Same resource both in the list and the detail response, like Find/RecordingUnit/Phase/
// Container — the list projection simply omits the detail-only fields server-side.
export type PlaceSummary = PlaceResource;
export type PlaceDetail = PlaceResource;
