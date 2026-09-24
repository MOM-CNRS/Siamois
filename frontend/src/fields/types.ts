// Mirrors fr.siamois.ui.api.openapi.v1.resource.form.FieldResource exactly (field-side of the
// "field vs field-configuration" split, plan §6) — this is the shared catalog entry, not a
// per-type config row. Keep in sync with that record if it changes.
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

/** Accepts either a raw value or a FieldAnswer envelope and always yields the raw value. */
export function unwrapAnswer(value: unknown): unknown {
  if (value != null && typeof value === "object" && "answerType" in value) {
    const envelope = value as FieldAnswerEnvelope;
    return "values" in envelope ? envelope.values : envelope.value;
  }
  return value;
}

export function resolveValueBinding(field: FieldResource): ResolvedBinding {
  // The answers map is keyed by field id (ProjectAnswersProjector, RecordingUnitOpenApiService),
  // never by valueBinding — those are different namespaces and only the id is stable.
  const answerKey = field.id;
  const propertyKey = field.valueBinding ?? field.id;

  return {
    read: (entity) => {
      const answers = (entity as EntityWithAnswers)?.answers;
      if (answers && answerKey in answers) {
        return unwrapAnswer(answers[answerKey]);
      }
      return field.isSystemField ? (entity as Record<string, unknown>)?.[propertyKey] : undefined;
    },
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
// "values: []" clears it.
export interface AnswerInputBody {
  value?: unknown;
  values?: unknown[] | null;
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
 */
export function toAnswerInput(field: FieldResource, value: unknown): AnswerInputBody {
  if (field.answerType.startsWith("SELECT_MULTIPLE")) {
    return { values: Array.isArray(value) ? value.map(toId) : [] };
  }
  return { value: toId(value) };
}
