import { apiFetch } from "../api/client";
import type { OptionsContext } from "../rules";
import type { FieldResource } from "./types";
import type { EntityKey } from "../entities/keys";

/**
 * Where a filter widget's option list comes from, for {@code answerType}s that reference another
 * resource (concepts, spatial units). Deliberately narrower than {@code fields/renderers.tsx}'s
 * edit-mode widgets (phase 4): a filter only ever needs an id + a label to build an "in" query
 * param, never the full write path.
 */
export interface FilterOption {
  id: string;
  label: string;
  // Extra info a concept suggestion shows (the JSF concept item): absent for every other source.
  concept?: ConceptItemInfo;
}

export interface ConceptItemInfo {
  thesaurusUrl?: string;
  /** The preferred label, only when `label` is an alternative one. */
  prefLabel?: string;
  definition?: string;
  parents?: string;
}

interface ConceptsResponseBody {
  data: ConceptResponseItem[];
}

interface ConceptResponseItem {
  id: string;
  resolvedLabel?: string | null;
  externalUrl?: string | null;
  definition?: string | null;
  thesaurusUrl?: string | null;
  prefLabel?: string | null;
  parents?: string | null;
}

function toConceptOption(c: ConceptResponseItem): FilterOption {
  const option: FilterOption = { id: c.id, label: c.resolvedLabel ?? c.externalUrl ?? c.id };
  const concept: ConceptItemInfo = {
    thesaurusUrl: c.thesaurusUrl ?? undefined,
    prefLabel: c.prefLabel ?? undefined,
    definition: c.definition ?? undefined,
    parents: c.parents ?? undefined,
  };
  if (Object.values(concept).some(Boolean)) option.concept = concept;
  return option;
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
  related?: { conceptId: string; projectId?: string },
): Promise<FilterOption[]> {
  const query = new URLSearchParams({ fieldCode });
  if (q) query.set("q", q);
  // A dependent list (rule options RELATED_CONCEPTS): the concepts related to the parent's answer.
  if (related) {
    query.set("relatedToConceptId", related.conceptId);
    if (related.projectId) query.set("projectId", related.projectId);
  }
  const body = await apiFetch<ConceptsResponseBody>(
    `/api/v1/organizations/${organizationId}/concepts?${query.toString()}`,
  );
  return body.data.map(toConceptOption);
}

interface PlaceAutocompleteResponseBody {
  data: { id: number; name: string; code?: string | null }[];
}

/**
 * GET /api/v1/places/autocomplete — mainLocation's option source. An empty query is the first page
 * of the organization's places by name: what a picker shows as soon as it opens.
 */
export async function fetchPlaceOptions(organizationId: number, q?: string): Promise<FilterOption[]> {
  const query = new URLSearchParams({ organizationId: String(organizationId) });
  if (q) query.set("q", q);
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
  // The registry entity type a picked value is — what its chip opens. Absent for targets with no
  // fiche of their own (concepts, persons).
  entityType?: EntityKey;
  createEntityType?: EntityKey;
}

const REFERENCE_TARGETS: [test: (answerType: string) => boolean, target: ReferenceTarget][] = [
  [(t) => t.includes("SPATIAL_UNIT"), { resourceType: "spatial-units", entityType: "place", createEntityType: "place" }],
  [(t) => t.endsWith("_PERSON"), { resourceType: "persons" }],
  [(t) => t.endsWith("_ACTION_UNIT"), { resourceType: "action-units", entityType: "project" }],
  [(t) => t.endsWith("_RECORDING_UNIT"), { resourceType: "recording-units", entityType: "recordingUnit", createEntityType: "recordingUnit" }],
  [(t) => t.endsWith("_SPECIMEN"), { resourceType: "finds", entityType: "find", createEntityType: "find" }],
  [(t) => t.endsWith("_CONTAINER"), { resourceType: "containers", entityType: "container", createEntityType: "container" }],
  [(t) => t.endsWith("_PHASE"), { resourceType: "phases", entityType: "phase", createEntityType: "phase" }],
];

export function referenceTargetOf(field: FieldResource): ReferenceTarget {
  return REFERENCE_TARGETS.find(([test]) => test(field.answerType))?.[1] ?? { resourceType: "concepts" };
}

/**
 * A legacy vocabulary field (SELECT_ONE / SELECT_MULTIPLE, no fieldCode): its suggestions come from
 * its own branch/collection restrictions, resolved server side by field id
 * (OrganizationProjectsControllerApi#getConcepts' fieldId mode), scoped to the edited entity's
 * project when there is one.
 */
async function fetchFieldConceptOptions(
  organizationId: number,
  fieldId: string,
  projectId?: string,
  q?: string,
  scope: OptionScope = {},
): Promise<FilterOption[]> {
  const query = new URLSearchParams({ fieldId });
  if (projectId) query.set("projectId", projectId);
  if (q) query.set("q", q);
  // The edited entity's type narrows the field's own restriction (a type-specific configuration).
  if (scope.valueConceptId) query.set("valueConceptId", scope.valueConceptId);
  const related = relatedConceptOf(scope.optionsContext);
  if (related) query.set("relatedToConceptId", related);
  const body = await apiFetch<ConceptsResponseBody>(
    `/api/v1/organizations/${organizationId}/concepts?${query.toString()}`,
  );
  return body.data.map(toConceptOption);
}

interface UsersResponseBody {
  data: { id: string; username?: string | null; name?: string | null; lastname?: string | null }[];
}

/** GET /api/v1/users — the organization's members (UsersControllerApi), searched by name/e-mail. */
async function fetchPersonOptions(organizationId: number, q?: string): Promise<FilterOption[]> {
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
  context?: OptionsContext,
): Promise<FilterOption[]> {
  const query = new URLSearchParams({ offset: "0", limit: String(PICKER_PAGE) });
  if (q) query.set("search", q);
  // Rule options REF_MATCH: only the candidates whose own field holds the parent's value — the
  // list's own reference filter (f.<fieldId>=<id>, FieldQueryService). An empty parent restricts
  // nothing (the server checks the same way).
  if (context?.kind === "REF_MATCH" && context.value != null) {
    query.set(`f.${context.candidateFieldId}`, context.value);
  }
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
 * What narrows a field's options beyond the field itself, when it is edited on an entity: the
 * entity's type (a type-specific vocabulary restriction) and the form's rules (a list depending on
 * another field's answer — rules/evaluate.ts's optionsContext).
 */
export interface OptionScope {
  valueConceptId?: string;
  optionsContext?: OptionsContext;
}

function relatedConceptOf(context: OptionsContext | undefined): string | undefined {
  return context?.kind === "RELATED_CONCEPTS" && context.relatedTo != null ? context.relatedTo : undefined;
}

/** A dependent list whose parent field is still empty offers nothing until the parent is answered. */
function waitsForParent(context: OptionsContext | undefined): boolean {
  if (context?.kind === "RELATED_CONCEPTS") return context.relatedTo == null;
  return false;
}

const NOTHING = async (): Promise<FilterOption[]> => [];

/**
 * The async option loader for a reference field, or null when it has none (scalars, and the
 * reference kinds that stay read-only: action codes, addresses). {@code projectId} scopes the
 * project-bound kinds; a filter (organization-wide list, no project) leaves it out.
 */
export function optionSourceFor(
  field: FieldResource,
  organizationId: number,
  projectId?: string,
  scope: OptionScope = {},
): ((q?: string) => Promise<FilterOption[]>) | null {
  if (waitsForParent(scope.optionsContext)) return NOTHING;
  switch (field.answerType) {
    case "SELECT_ONE_FROM_FIELD_CODE":
    case "SELECT_MULTIPLE_FROM_FIELD_CODE": {
      if (!field.fieldCode) return null;
      const related = relatedConceptOf(scope.optionsContext);
      return (q) =>
        fetchConceptOptions(organizationId, field.fieldCode as string, q, related ? { conceptId: related, projectId } : undefined);
    }
    case "SELECT_ONE":
    case "SELECT_MULTIPLE":
      return (q) => fetchFieldConceptOptions(organizationId, field.id, projectId, q, scope);
    case "SELECT_ONE_SPATIAL_UNIT":
    case "SELECT_MULTIPLE_SPATIAL_UNIT_TREE":
      return (q) => fetchPlaceOptions(organizationId, q);
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
      return (q) => fetchEntityOptions(segments, organizationId, projectId, q, scope.optionsContext);
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
