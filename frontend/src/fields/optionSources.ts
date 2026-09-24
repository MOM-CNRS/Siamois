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

/**
 * GET /api/v1/places/autocomplete — mainLocation's option source.
 *
 * <p>Unlike the concepts endpoint, this one <strong>requires</strong> {@code q}: an empty or blank
 * value is a 400 ({@code PlaceSearchControllerApi#autocomplete}, "q ne doit pas être vide"). There
 * is no "list every place" mode to fall back on, and there could not sensibly be one — a whole
 * institution's spatial units is not a picker list. Callers that open a picker before anything is
 * typed must therefore not call this with an empty query; {@link optionSourceFor} enforces that.</p>
 */
export async function fetchPlaceOptions(organizationId: number, q: string): Promise<FilterOption[]> {
  const query = new URLSearchParams({ organizationId: String(organizationId), q });
  const body = await apiFetch<PlaceAutocompleteResponseBody>(`/api/v1/places/autocomplete?${query.toString()}`);
  return body.data.map((p) => ({ id: String(p.id), label: p.name }));
}

/**
 * What a reference field points at: the {@code resourceType} its ResourceRefs carry (the same one
 * FieldAnswerWireService emits server side, so a picked value and a loaded one compare equal), and
 * the registry key of the entity type a « Nouveau » in its picker creates — absent for targets
 * that are not created from a field (concepts, persons, projects: decision of 2026-09-24, "tout
 * sauf le projet").
 */
export interface ReferenceTarget {
  resourceType: string;
  createEntityType?: string;
}

const REFERENCE_TARGETS: [test: (answerType: string) => boolean, target: ReferenceTarget][] = [
  [(t) => t.includes("SPATIAL_UNIT"), { resourceType: "spatial-units", createEntityType: "place" }],
  [(t) => t.endsWith("_PERSON"), { resourceType: "persons" }],
  [(t) => t.endsWith("_ACTION_UNIT"), { resourceType: "action-units" }],
  [(t) => t.endsWith("_RECORDING_UNIT"), { resourceType: "recording-units", createEntityType: "recordingUnit" }],
  [(t) => t.endsWith("_SPECIMEN"), { resourceType: "finds", createEntityType: "find" }],
  [(t) => t.endsWith("_CONTAINER"), { resourceType: "containers", createEntityType: "container" }],
  [(t) => t.endsWith("_PHASE"), { resourceType: "phases", createEntityType: "phase" }],
];

export function referenceTargetOf(field: FieldResource): ReferenceTarget {
  return REFERENCE_TARGETS.find(([test]) => test(field.answerType))?.[1] ?? { resourceType: "concepts" };
}

/**
 * Whether a field's option source can answer an empty query — i.e. whether a picker may show a
 * starting list before the user types anything.
 *
 * <p>Concepts can (GET /api/v1/organizations/{id}/concepts with no {@code q} returns the field's
 * whole vocabulary, paginated), and so can every paged entity list; spatial units cannot (see
 * {@link fetchPlaceOptions}). Exposed so a caller can tell "nothing matches" from "type something
 * first".</p>
 */
const SPATIAL_ANSWER_TYPES = new Set(["SELECT_ONE_SPATIAL_UNIT", "SELECT_MULTIPLE_SPATIAL_UNIT_TREE"]);

export function supportsEmptyQuery(field: FieldResource): boolean {
  return !SPATIAL_ANSWER_TYPES.has(field.answerType);
}

/**
 * A legacy vocabulary field (SELECT_ONE / SELECT_MULTIPLE, no fieldCode): its suggestions come from
 * its own branch/collection restrictions, resolved server side by field id
 * (OrganizationProjectsControllerApi#getConcepts' fieldId mode), scoped to the edited entity's
 * project when there is one.
 */
export async function fetchFieldConceptOptions(
  organizationId: number,
  fieldId: string,
  projectId?: string,
  q?: string,
): Promise<FilterOption[]> {
  const query = new URLSearchParams({ fieldId });
  if (projectId) query.set("projectId", projectId);
  if (q) query.set("q", q);
  const body = await apiFetch<ConceptsResponseBody>(
    `/api/v1/organizations/${organizationId}/concepts?${query.toString()}`,
  );
  return body.data.map((c) => ({ id: c.id, label: c.resolvedLabel ?? c.externalUrl ?? c.id }));
}

interface UsersResponseBody {
  data: { id: string; username?: string | null; name?: string | null; lastname?: string | null }[];
}

/** GET /api/v1/users — the organization's members (UsersControllerApi), searched by name/e-mail. */
export async function fetchPersonOptions(organizationId: number, q?: string): Promise<FilterOption[]> {
  const query = new URLSearchParams({ organizationId: String(organizationId), limit: String(PICKER_PAGE) });
  if (q) query.set("search", q);
  const body = await apiFetch<UsersResponseBody>(`/api/v1/users?${query.toString()}`);
  return body.data.map((p) => {
    const fullName = [p.name, p.lastname].filter(Boolean).join(" ");
    return { id: String(p.id), label: fullName || p.username || String(p.id) };
  });
}

interface EntityRowsResponseBody {
  data: {
    id: string | number;
    fullIdentifier?: string | null;
    identifier?: string | null;
    title?: string | null;
    name?: string | null;
  }[];
}

// One page is all a picker shows: past that the user narrows by typing.
const PICKER_PAGE = 20;

/** The label an entity row is known by — the same one its own list's identifier column shows. */
export function entityRowLabel(row: EntityRowsResponseBody["data"][number]): string {
  return row.fullIdentifier ?? row.title ?? row.name ?? row.identifier ?? String(row.id);
}

/**
 * A page of entity rows as picker options: the project's own sub-collection when the edited entity
 * has a project (the server refuses a reference to another project's entity anyway —
 * FieldAnswerPatchService#inProject), the organization-wide list otherwise.
 */
async function fetchEntityOptions(
  segments: { projectPath: string; orgPath: string },
  organizationId: number,
  projectId: string | undefined,
  q?: string,
): Promise<FilterOption[]> {
  const query = new URLSearchParams({ offset: "0", limit: String(PICKER_PAGE) });
  if (q) query.set("search", q);
  let path: string;
  if (projectId) {
    path = `/api/v1/projects/${projectId}/${segments.projectPath}`;
  } else {
    query.set("organizationId", String(organizationId));
    path = `/api/v1/${segments.orgPath}`;
  }
  const body = await apiFetch<EntityRowsResponseBody>(`${path}?${query.toString()}`);
  return body.data.map((row) => ({ id: String(row.id), label: entityRowLabel(row) }));
}

const ENTITY_SEGMENTS: Record<string, { projectPath: string; orgPath: string }> = {
  "recording-units": { projectPath: "recording-units", orgPath: "recording-units" },
  finds: { projectPath: "mobiliers", orgPath: "finds" },
  phases: { projectPath: "phases", orgPath: "phases" },
  containers: { projectPath: "containers", orgPath: "containers" },
};

/**
 * The async option loader for a reference field, or null when it has none (scalars, and the
 * reference kinds that stay read-only: action codes, addresses). {@code projectId} scopes the
 * project-bound kinds; a filter (organization-wide list, no project) leaves it out.
 */
export function optionSourceFor(
  field: FieldResource,
  organizationId: number,
  projectId?: string,
): ((q?: string) => Promise<FilterOption[]>) | null {
  switch (field.answerType) {
    case "SELECT_ONE_FROM_FIELD_CODE":
    case "SELECT_MULTIPLE_FROM_FIELD_CODE":
      return field.fieldCode ? (q) => fetchConceptOptions(organizationId, field.fieldCode as string, q) : null;
    case "SELECT_ONE":
    case "SELECT_MULTIPLE":
      return (q) => fetchFieldConceptOptions(organizationId, field.id, projectId, q);
    case "SELECT_ONE_SPATIAL_UNIT":
    case "SELECT_MULTIPLE_SPATIAL_UNIT_TREE":
      // An empty query would be a 400 here, so it short-circuits to no options rather than firing
      // a request that cannot succeed — the picker simply stays empty until something is typed.
      return (q) => (q && q.trim() ? fetchPlaceOptions(organizationId, q) : Promise.resolve([]));
    case "SELECT_ONE_PERSON":
    case "SELECT_MULTIPLE_PERSON":
      return (q) => fetchPersonOptions(organizationId, q);
    case "SELECT_ONE_ACTION_UNIT":
      return (q) => fetchEntityOptions({ projectPath: "", orgPath: "projects" }, organizationId, undefined, q);
    case "SELECT_ONE_RECORDING_UNIT":
    case "SELECT_MULTIPLE_RECORDING_UNIT":
    case "SELECT_MULTIPLE_SPECIMEN":
    case "SELECT_MULTIPLE_PHASE":
    case "SELECT_MULTIPLE_CONTAINER": {
      const segments = ENTITY_SEGMENTS[referenceTargetOf(field).resourceType];
      return (q) => fetchEntityOptions(segments, organizationId, projectId, q);
    }
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
export type FilterKind = "contains" | "concept-one" | "concept-many" | "spatial-one" | "range" | "date-range" | "in";

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

/**
 * The filter widget for a catalog column, from what the server says the column accepts
 * ({@link FieldResource.query}) — keyed {@code f.<fieldId>}, for a system field and an additional
 * one alike. Null when the column has no filter.
 */
export function filterKindForField(field: FieldResource): FilterKind | null {
  switch (field.query?.filterOp) {
    case "contains":
      return "contains";
    case "range":
      return "range";
    case "date-range":
      return "date-range";
    case "in":
      return "in";
    default:
      return null;
  }
}
