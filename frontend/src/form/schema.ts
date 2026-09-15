/**
 * TypeScript mirror of the server's form schema wire shapes. Kept in exact lockstep with:
 * - fr.siamois.ui.api.openapi.v1.resource.form.{FormResource,FieldResource,FieldAnswer,AnswerInput}
 * - fr.siamois.ui.form.dto.{FormUiDto,CustomFormPanelUiDto,CustomRowUiDto,CustomColUiDto}
 * - fr.siamois.domain.models.form.customform.{EnabledWhenJson,DependsOnJson}
 *
 * Do not "clean up" field names to be more idiomatic TS — they intentionally match the Java JSON
 * keys (e.g. `isRequired`/`isReadOnly`, not `required`/`readOnly`) so a wire sample can be diffed
 * against these types directly.
 */

// ---- answerType -----------------------------------------------------------------------------

/**
 * Every `@DiscriminatorValue` string across CustomField subclasses (see RecordingUnitOpenApiService
 * .answerTypeDiscriminator). NOTE: "DECIMAL" has no dedicated FieldAnswer subtype server-side — it is
 * serialized as a TextFieldAnswer with a stringified value (RecordingUnitOpenApiService.toTypedAnswer
 * default branch). Render it with the decimal input, but read/write it through the text shape.
 */
export type FieldAnswerType =
  | "TEXT"
  | "INTEGER"
  | "DECIMAL"
  | "DATETIME"
  | "SELECT_ONE"
  | "SELECT_MULTIPLE"
  | "SELECT_ONE_FROM_FIELD_CODE"
  | "SELECT_MULTIPLE_FROM_FIELD_CODE"
  | "SELECT_ONE_PERSON"
  | "SELECT_MULTIPLE_PERSON"
  | "SELECT_ONE_ACTION_UNIT"
  | "SELECT_ONE_ACTION_CODE"
  | "SELECT_ONE_SPATIAL_UNIT"
  | "SELECT_ADDRESS"
  | "SELECT_MULTIPLE_SPATIAL_UNIT_TREE"
  | "SELECT_ONE_RECORDING_UNIT"
  | "SELECT_MULTIPLE_RECORDING_UNIT"
  | "MEASUREMENT"
  | "SELECT_MULTIPLE_SPECIMEN"
  | "SELECT_MULTIPLE_CONTAINER"
  | "SELECT_MULTIPLE_PHASE";

/** Every SELECT_ONE_* type shares the SelectOneFieldAnswer wire shape server-side. */
export const SELECT_ONE_TYPES: ReadonlySet<FieldAnswerType> = new Set([
  "SELECT_ONE",
  "SELECT_ONE_FROM_FIELD_CODE",
  "SELECT_ONE_PERSON",
  "SELECT_ONE_ACTION_UNIT",
  "SELECT_ONE_ACTION_CODE",
  "SELECT_ONE_SPATIAL_UNIT",
  "SELECT_ADDRESS",
  "SELECT_ONE_RECORDING_UNIT",
]);

/** Every SELECT_MULTIPLE_* type shares the SelectManyFieldAnswer wire shape server-side. */
export const SELECT_MANY_TYPES: ReadonlySet<FieldAnswerType> = new Set([
  "SELECT_MULTIPLE",
  "SELECT_MULTIPLE_FROM_FIELD_CODE",
  "SELECT_MULTIPLE_PERSON",
  "SELECT_MULTIPLE_SPATIAL_UNIT_TREE",
  "SELECT_MULTIPLE_RECORDING_UNIT",
  "SELECT_MULTIPLE_SPECIMEN",
  "SELECT_MULTIPLE_CONTAINER",
  "SELECT_MULTIPLE_PHASE",
]);

// ---- field metadata (fr.siamois.ui.api.openapi.v1.resource.form.FieldResource) ------------------

export interface FieldResource {
  id: string;
  resourceType: "fields";
  label: string;
  answerType: FieldAnswerType;
  hint?: string | null;
  isSystemField: boolean;
  valueBinding?: string | null;
  /** Only set for SELECT_*_FROM_FIELD_CODE types. */
  fieldCode?: string | null;
}

// ---- layout (fr.siamois.ui.form.dto.*, serialized via FormUiDtoLayoutJson) ---------------------

/**
 * enabledWhenSpec wire shape (fr.siamois.domain.models.form.customform.EnabledWhenJson). Op.IN is an
 * OR across `values`; there is no AND/OR combinator across multiple conditions — a column has at most
 * one enabledWhen.
 */
export interface EnabledWhenJson {
  op: "EQ" | "NEQ" | "IN";
  fieldId: number;
  values: EnabledWhenValueJson[];
}

export interface EnabledWhenValueJson {
  /** Fully-qualified Java class name of the answer type being matched. Only one value is actually
   *  implemented server-side (see rulesEngine.ts) — every other value always fails to match. */
  answerClass: string;
  value: unknown;
}

/** dependsOnSpec wire shape — NOT a visibility condition, only seeds cascading concept autocompletes. */
export interface DependsOnJson {
  fieldId: number;
}

export interface CustomColUiDto {
  className?: string;
  /** JSON key is literally "isRequired", not "required" — matches the server wire shape as-is. */
  isRequired?: boolean;
  /** JSON key is literally "isReadOnly", not "readOnly". */
  isReadOnly?: boolean;
  /** Absent when the column has no bound field (e.g. a purely decorative row). */
  fieldId?: number;
  enabledWhen?: EnabledWhenJson;
  dependsOn?: DependsOnJson;
}

export interface CustomRowUiDto {
  columns: CustomColUiDto[];
}

export interface CustomFormPanelUiDto {
  className?: string;
  /** An i18n label CODE, not resolved text — resolve it the same way the JSF panel titles do. */
  name?: string;
  canUserAddFields?: boolean | null;
  isSystemPanel?: boolean | null;
  rows: CustomRowUiDto[];
}

export type FormLayout = CustomFormPanelUiDto[];

/**
 * `FormResource.layoutJson` is a JSON STRING inside the outer JSON, not a nested object — the server
 * hand-builds it via FormUiDtoLayoutJson.serialize rather than Jackson-serializing FormUiDto directly.
 * Empty/absent layout serializes as the literal string "[]". Parse defensively: anything that isn't
 * an array (a stray legacy "{}" e.g.) is treated as an empty layout rather than thrown.
 */
export function parseLayoutJson(layoutJson: string | null | undefined): FormLayout {
  if (!layoutJson) return [];
  let parsed: unknown;
  try {
    parsed = JSON.parse(layoutJson);
  } catch {
    return [];
  }
  return Array.isArray(parsed) ? (parsed as FormLayout) : [];
}

// ---- answers (fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer / AnswerInput) -------------

export interface ResourceRef {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

export interface MeasurementRef {
  numericValue?: number | null;
  symbol?: string | null;
  normalizedValue?: number | null;
  comment?: string | null;
}

interface BaseFieldAnswer {
  answerType: FieldAnswerType;
  field: FieldResource;
}

export interface TextFieldAnswer extends BaseFieldAnswer {
  value: string | null;
}
export interface IntegerFieldAnswer extends BaseFieldAnswer {
  value: number | null;
}
/** ISO-8601 UTC string, e.g. "2024-03-15T00:00:00Z". */
export interface DateFieldAnswer extends BaseFieldAnswer {
  value: string | null;
}
export interface SelectOneFieldAnswer extends BaseFieldAnswer {
  value: ResourceRef | null;
}
export interface SelectManyFieldAnswer extends BaseFieldAnswer {
  values: ResourceRef[];
}
export interface MeasurementFieldAnswer extends BaseFieldAnswer {
  value: MeasurementRef | null;
}

/**
 * Discriminated union mirroring the sealed FieldAnswer interface. A DECIMAL field answer arrives
 * shaped as a TextFieldAnswer (answerType:"DECIMAL", value: stringified number) — there is no
 * dedicated DecimalFieldAnswer variant server-side.
 */
export type FieldAnswer =
  | TextFieldAnswer
  | IntegerFieldAnswer
  | DateFieldAnswer
  | SelectOneFieldAnswer
  | SelectManyFieldAnswer
  | MeasurementFieldAnswer;

/**
 * Write-side shape for RecordingUnitPatchRequest.answers. No discriminator: the server infers the
 * target field's type from the fieldId key in the containing map. `value: null` clears a scalar/
 * select-one field; `values: []` clears a select-many field; an absent key leaves the field untouched.
 */
export interface AnswerInput {
  value?: unknown;
  values?: unknown[];
}
