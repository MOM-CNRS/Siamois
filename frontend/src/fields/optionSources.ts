import { apiFetch } from "../api/client";
import type { FieldResource } from "./types";

/**
 * Where a filter widget's option list comes from, for {@code answerType}s that reference another
 * resource (concepts, spatial units). Deliberately narrower than {@code fields/renderers.tsx}'s
 * edit-mode widgets (phase 4): a filter only ever needs an id + a label to build an "in" query
 * param, never the full write path.
 */
export interface FilterOption {
  id: string;
  label: string;
}

interface ConceptsResponseBody {
  data: { id: string; resolvedLabel?: string | null; externalUrl?: string | null }[];
}

/**
 * GET /api/v1/organizations/{id}/concepts — the org-scoped sibling of
 * GET /api/v1/projects/{id}/concepts, used here because a list filter has an organization in
 * scope but no project to derive one from (OrganizationProjectsControllerApi#getConcepts).
 */
export async function fetchConceptOptions(
  organizationId: number,
  fieldCode: string,
  q?: string,
): Promise<FilterOption[]> {
  const query = new URLSearchParams({ fieldCode });
  if (q) query.set("q", q);
  const body = await apiFetch<ConceptsResponseBody>(
    `/api/v1/organizations/${organizationId}/concepts?${query.toString()}`,
  );
  return body.data.map((c) => ({ id: c.id, label: c.resolvedLabel ?? c.externalUrl ?? c.id }));
}

interface PlaceAutocompleteResponseBody {
  data: { id: number; name: string; code?: string | null }[];
}

/** GET /api/v1/places/autocomplete — mainLocation's option source. */
export async function fetchPlaceOptions(organizationId: number, q: string): Promise<FilterOption[]> {
  const query = new URLSearchParams({ organizationId: String(organizationId), q });
  const body = await apiFetch<PlaceAutocompleteResponseBody>(`/api/v1/places/autocomplete?${query.toString()}`);
  return body.data.map((p) => ({ id: String(p.id), label: p.name }));
}

/**
 * Whether a filterable field needs an async option source (concept/spatial autocomplete) versus a
 * plain text/number/range widget. Mirrors the answerTypes ActionUnitTableColumnDefaults' default-
 * visible columns actually use: SELECT_ONE_FROM_FIELD_CODE (status), SELECT_ONE_SPATIAL_UNIT
 * (mainLocation) — periods/subjects (SELECT_MULTIPLE_FROM_FIELD_CODE) use the same concept source.
 */
export function optionSourceFor(
  field: FieldResource,
  organizationId: number,
): ((q?: string) => Promise<FilterOption[]>) | null {
  switch (field.answerType) {
    case "SELECT_ONE_FROM_FIELD_CODE":
    case "SELECT_MULTIPLE_FROM_FIELD_CODE":
      return field.fieldCode ? (q) => fetchConceptOptions(organizationId, field.fieldCode as string, q) : null;
    case "SELECT_ONE_SPATIAL_UNIT":
      return (q) => fetchPlaceOptions(organizationId, q ?? "");
    default:
      return null;
  }
}


/**
 * Which {@code f.<key>} filter widget an answerType needs, mirroring
 * {@code ProjectListFilter}'s {@code Kind} whitelist. {@code null} means the column has no filter
 * (most of the catalog — only the default-visible columns are whitelisted server-side; sending
 * {@code f.<key>} for anything else is a 400, so the UI never offers one for those).
 */
export type FilterKind = "contains" | "concept-one" | "concept-many" | "spatial-one" | "range";

const FILTER_KIND_BY_ANSWER_TYPE: Record<string, FilterKind> = {
  TEXT: "contains",
  DECIMAL: "range",
  SELECT_ONE_FROM_FIELD_CODE: "concept-one",
  SELECT_MULTIPLE_FROM_FIELD_CODE: "concept-many",
  SELECT_ONE_SPATIAL_UNIT: "spatial-one",
};

export function filterKindForAnswerType(answerType: string): FilterKind | null {
  return FILTER_KIND_BY_ANSWER_TYPE[answerType] ?? null;
}
