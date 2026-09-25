import { apiFetch } from "../../api/client";
import { fetchList } from "../listApi";
import type { AnswerInputBody } from "../../fields/types";
import type { ListParams, PagedResult } from "../types";
import type { PlaceDetail, PlaceSummary } from "./types";

interface PlaceResponseBody {
  data: PlaceDetail;
}

// GET /api/v1/places?organizationId=… (organization-wide list, OrganizationListsControllerApi),
// through the shared fetchList like every other entity's list.
export async function listPlaces(params: ListParams): Promise<PagedResult<PlaceSummary>> {
  return fetchList<PlaceSummary>("places", params);
}

export async function getPlace(id: string | number): Promise<PlaceDetail> {
  const body = await apiFetch<PlaceResponseBody>(`/api/v1/places/${id}`);
  return body.data;
}

// PlacePatchRequest has no generic `answers` map (unlike Phase/Container) — it's the flat
// name/typeConceptId/address/placeNumber/geom shape PlaceControllerApi.patch already exposed
// before this fiche existed. This adapter translates the field-id-keyed answers FieldEditCell/
// CellEditOverlay produce (same call shape every other fiche's patchAnswers uses) into that flat
// request, so PlaceFicheTab.tsx can reuse the same generic editing path as Phase/Container/etc.
// without PlacePatchRequest itself changing. Deliberately does not support the address field —
// see types.ts's own note on why `answers` never carries one to translate in the first place.
export async function patchPlaceAnswers(
  id: string | number,
  answers: Record<string, AnswerInputBody>,
): Promise<PlaceDetail> {
  const body: Record<string, unknown> = {};
  for (const [fieldId, input] of Object.entries(answers)) {
    const value = input.value;
    switch (fieldId) {
      case String(NAME_FIELD_ID):
        body.name = value;
        break;
      case String(TYPE_FIELD_ID):
        body.typeConceptId = value == null ? null : Number(value);
        break;
      case String(PLACE_NUMBER_FIELD_ID):
        body.placeNumber = value;
        break;
      // CODE_FIELD (-203) is read-only in SpatialUnit.DETAILS_FORM (server-generated) and the
      // address has no editor, so neither should reach this: fail loudly rather than report a
      // save that silently wrote nothing.
      default:
        throw new Error(`Champ ${fieldId} non modifiable sur un lieu`);
    }
  }
  const response = await apiFetch<PlaceResponseBody>(`/api/v1/places/${id}`, {
    method: "PATCH",
    body,
  });
  return response.data;
}

// SpatialUnit's own field ids (SpatialUnit.NAME_FIELD/.SPATIAL_UNIT_TYPE_FIELD/.PLACE_NUMBER_FIELD
// in Java) — negative, stable, hand-assigned system-field ids, mirrored here the same way
// entities/find/findTypes.ts and others key off a fixed field id rather than re-deriving it from
// valueBinding (category/type namespaces collide across entities, id doesn't).
const NAME_FIELD_ID = -202;
const TYPE_FIELD_ID = -201;
const PLACE_NUMBER_FIELD_ID = -205;

// Mirrors PlaceCreateRequest's required trio — address/placeNumber/geom stay editable afterward
// on the fiche, same reduction as the other create overlays.
export interface PlaceCreateBody {
  organizationId: number;
  name: string;
  typeConceptId: string;
  // The new place becomes a direct child of parentPlaceId / the direct parent of childPlaceId.
  parentPlaceId?: string | number;
  childPlaceId?: string | number;
}

interface PlaceCreatedResponseBody {
  data: { id: number; name: string; code?: string | null };
}

export async function createPlace(body: PlaceCreateBody): Promise<{ id: number }> {
  const response = await apiFetch<PlaceCreatedResponseBody>("/api/v1/places", {
    method: "POST",
    body: {
      organizationId: body.organizationId,
      name: body.name,
      typeConceptId: Number(body.typeConceptId),
      parentPlaceId: body.parentPlaceId != null ? Number(body.parentPlaceId) : undefined,
      childPlaceId: body.childPlaceId != null ? Number(body.childPlaceId) : undefined,
    },
  });
  return response.data;
}

// POST /api/v1/places/{id}/duplicate — same fields and parents, named "name (n)".
export async function duplicatePlace(id: string | number): Promise<{ id: number }> {
  const response = await apiFetch<PlaceCreatedResponseBody>(`/api/v1/places/${id}/duplicate`, { method: "POST" });
  return response.data;
}
