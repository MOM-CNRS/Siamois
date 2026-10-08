// Mirrors fr.siamois.ui.api.openapi.v1.resource.form.FieldResource exactly (field-side of the
// "field vs field-configuration" split, plan §6) — this is the shared catalog entry, not a
// per-type config row. Keep in sync with that record if it changes.
import type { FieldRules } from "../rules/types";

export interface FieldResource {
  id: string;
  resourceType: string;
  label: string;
  answerType: string;
  hint?: string | null;
  isSystemField: boolean;
  valueBinding?: string | null;
  fieldCode?: string | null;
  // TEXT only — CustomFieldText.isTextArea, which decides p:inputTextarea vs p:inputText in JSF
  // (pages/shared/inplace/text.xhtml). Null/absent for every other answerType.
  isTextArea?: boolean | null;
  // CustomField.getIcon() — the icon on panelField.xhtml's label button. Every field currently
  // answers "bi bi-question" unless its subclass overrides getIcon().
  icon?: string | null;
  // CustomField.getConceptUri() — the target of panelField.xhtml's "Documentation" menu item.
  // Null when the field has no concept (or the concept has no vocabulary).
  conceptUri?: string | null;
  // Entry constraints (FieldResource.Constraints): numeric bounds for INTEGER/DECIMAL/MEASUREMENT,
  // showTime for DATETIME, the unit symbol for MEASUREMENT. Null when the field has none.
  constraints?: FieldConstraints | null;
  // What a list accepts on this field's column (FieldResource.Query): sort=<id>:asc|desc and
  // f.<id>… Null/absent when the column can be neither sorted nor filtered.
  query?: FieldQueryCapability | null;
  // Never editable, in the fiche as in a list (a readOnly column of the details form: the project an
  // entity belongs to, a generated identifier). Absent = editable.
  readOnly?: boolean | null;
  // The conditional rules of the field's column in its entity's details form (FieldQueryService):
  // what a list, having no layout, evaluates to grey a cell out, bound it or flag its value.
  rules?: FieldRules | null;
  // External sources a place field suggests from besides the organization's places (INSEE: communes,
  // GEOPLAT: addresses); absent = the organization's places only.
  placeSources?: string[] | null;
}

/** The field holding the project an entity belongs to: set at creation, never edited, and — as a
 * list column — shown by default only where rows come from several projects. */
export function isOwningProjectField(field: FieldResource): boolean {
  return field.answerType === "SELECT_ONE_ACTION_UNIT" && field.valueBinding === "actionUnit";
}

export interface FieldQueryCapability {
  sortable: boolean;
  // contains → f.<id>=text; range / date-range → f.<id>.from / .to; in → f.<id>=<id> (repeatable).
  filterOp?: "contains" | "range" | "date-range" | "in" | null;
}

export interface FieldConstraints {
  min?: number | null;
  max?: number | null;
  showTime?: boolean | null;
  unit?: string | null;
}

/**
 * Resolves a field's valueBinding into read/write functions against an entity object.
 *
 * Three shapes reach this code, and one read path has to serve all of them:
 * - a list row from GET /api/v1/projects?fields=…, whose `answers` map is keyed by field id and
 *   holds RAW values (ProjectAnswersProjector deliberately omits the FieldAnswer envelope: it
 *   embeds a whole FieldResource per answer, i.e. 33 copies of the catalog per row);
 * - a RecordingUnit/Find detail response, whose `answers` map holds the WRAPPED FieldAnswer
 *   envelope ({answerType, field, value|values}) — see RecordingUnitOpenApiService#toTypedAnswer;
 * - a flat entity with no `answers` at all (GET /api/v1/projects/{id}), where a system field's
 *   value lives at its own valueBinding property.
 *
 * So: read the answers map first (unwrapping an envelope if that is what is there), and fall back
 * to the system-field property path. Writes stay split — a system field writes its property, so
 * FicheTab keeps building a flat ProjectPatchRequest; anything else writes into `answers`.
 */
export interface ResolvedBinding {
  read(entity: unknown): unknown;
  // The answer as served, before unwrapAnswer: what a display needs to show a multi-valued
  // answer's total ("+N") and fetch the rest of it (MultiValueAnswer).
  readRaw(entity: unknown): unknown;
  write<T extends object>(entity: T, value: unknown): T;
}

interface EntityWithAnswers {
  answers?: Record<string, unknown>;
}

interface FieldAnswerEnvelope {
  answerType: string;
  value?: unknown;
  values?: unknown;
}

/**
 * A multi-valued answer as the API serves it — MultiValue on a raw answer, the same properties on
 * the SelectManyFieldAnswer envelope: at most `valuesLimit` values, how many there are in all, and
 * where the rest is when `values` isn't all of them.
 */
export interface MultiValueAnswer {
  values: unknown[];
  total: number;
  complete: boolean;
  _links?: { values: string } | null;
}

function isMultiValue(value: unknown): value is MultiValueAnswer {
  return (
    value != null &&
    typeof value === "object" &&
    Array.isArray((value as MultiValueAnswer).values) &&
    typeof (value as MultiValueAnswer).total === "number"
  );
}

/** Accepts either a raw value or a FieldAnswer envelope and always yields the raw value. */
export function unwrapAnswer(value: unknown): unknown {
  if (isMultiValue(value)) return value.values;
  if (value != null && typeof value === "object" && "answerType" in value) {
    const envelope = value as FieldAnswerEnvelope;
    return "values" in envelope ? envelope.values : envelope.value;
  }
  return value;
}

/**
 * The multi-valued answer `value` holds — its preview, total and completeness — or null for any
 * other value. A plain array (an entity property, a value the client built itself) is complete.
 */
export function readMultiValue(value: unknown): MultiValueAnswer | null {
  if (isMultiValue(value)) return value;
  if (Array.isArray(value)) return { values: value, total: value.length, complete: true };
  return null;
}

export function resolveValueBinding(field: FieldResource): ResolvedBinding {
  // The answers map is keyed by field id (ProjectAnswersProjector, RecordingUnitOpenApiService),
  // never by valueBinding — those are different namespaces and only the id is stable.
  const answerKey = field.id;
  const propertyKey = field.valueBinding ?? field.id;

  const readRaw = (entity: unknown): unknown => {
    const answers = (entity as EntityWithAnswers)?.answers;
    if (answers && answerKey in answers) {
      return answers[answerKey];
    }
    return field.isSystemField ? (entity as Record<string, unknown>)?.[propertyKey] : undefined;
  };

  return {
    read: (entity) => unwrapAnswer(readRaw(entity)),
    readRaw,
    write: <T extends object>(entity: T, value: unknown): T => {
      if (field.isSystemField) {
        return { ...entity, [propertyKey]: value } as T;
      }
      const withAnswers = entity as T & EntityWithAnswers;
      return {
        ...withAnswers,
        answers: { ...withAnswers.answers, [answerKey]: value },
      } as T;
    },
  };
}


// Mirrors fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput — the write-side counterpart of
// FieldAnswer. "value: null" clears a scalar/single-reference field; "values: null" leaves a
// SELECT_MANY field untouched (ProjectPatchRequest/RecordingUnitPatchRequest's shared convention);
// "values: []" clears it. "add"/"remove" change a SELECT_MANY field by the ids they name, leaving
// every other value alone — the only safe write when what was read was only a preview.
export interface AnswerInputBody {
  value?: unknown;
  values?: unknown[] | null;
  add?: unknown[];
  remove?: unknown[];
}

interface ResourceRefLike {
  resourceId?: string;
  id?: string;
}

function toId(value: unknown): unknown {
  if (value != null && typeof value === "object") {
    const ref = value as ResourceRefLike;
    if (ref.resourceId != null) return ref.resourceId;
    if (ref.id != null) return ref.id;
  }
  return value;
}

/**
 * Converts a field renderer's onChange value (fields/renderers.tsx: a scalar, a ResourceRef-like
 * object/array, or null) into the AnswerInput shape a PATCH body sends. The inverse of what
 * resolveValueBinding's read side does with unwrapAnswer — same field, opposite direction.
 *
 * <p>Given the value the editor started from, a multi-valued field is written as the difference
 * (add/remove) rather than as a whole list: that start may have been a preview of a longer list,
 * and a whole list would drop every value it never showed.</p>
 */
export function toAnswerInput(field: FieldResource, value: unknown, previous?: unknown): AnswerInputBody {
  if (field.answerType.startsWith("SELECT_MULTIPLE")) {
    const next = Array.isArray(value) ? value.map(toId) : [];
    if (previous === undefined) return { values: next };
    const before = Array.isArray(previous) ? previous.map(toId) : [];
    const key = (id: unknown) => String(id);
    const beforeKeys = new Set(before.map(key));
    const nextKeys = new Set(next.map(key));
    return {
      add: next.filter((id) => !beforeKeys.has(key(id))),
      remove: before.filter((id) => !nextKeys.has(key(id))),
    };
  }
  return { value: toId(value) };
}
