/**
 * Pure conversion between the server's read-side FieldAnswer shape and write-side AnswerInput shape,
 * and the plain JS value a field renderer actually edits. Kept separate from useEntityForm so it can
 * be unit-tested without React.
 */
import { SELECT_MANY_TYPES, SELECT_ONE_TYPES } from "./schema";
import type { AnswerInput, FieldAnswer, FieldAnswerType, MeasurementRef, ResourceRef } from "./schema";

/**
 * The value shape a field renderer works with locally:
 * - TEXT/DECIMAL: string | null
 * - INTEGER: number | null
 * - DATETIME: ISO string | null
 * - SELECT_ONE_*: ResourceRef | null
 * - SELECT_MULTIPLE_*: ResourceRef[]
 * - MEASUREMENT: MeasurementRef | null
 */
export type LocalValue = string | number | ResourceRef | ResourceRef[] | MeasurementRef | null;

/** Extracts the renderer-facing local value out of a server FieldAnswer. */
export function toLocalValue(answer: FieldAnswer): LocalValue {
  if (SELECT_MANY_TYPES.has(answer.answerType)) {
    return "values" in answer ? answer.values : [];
  }
  // TextFieldAnswer / IntegerFieldAnswer / DateFieldAnswer / SelectOneFieldAnswer / MeasurementFieldAnswer
  // all carry a single nullable `value` — TS can't narrow the union generically here without a switch,
  // but the runtime shape is uniform enough that a direct read is safe and keeps this function simple.
  return "value" in answer ? (answer.value as LocalValue) : null;
}

/**
 * Builds the AnswerInput to PATCH for a locally-edited value. `null`/empty clears the field, matching
 * RecordingUnitPatchRequest semantics ("value:null = vider. values:[] = vider multi.").
 *
 * KNOWN GAP: the exact write shape for MEASUREMENT hasn't been verified against a live server response
 * (only inferred from test names in RecordingUnitOpenApiServiceTest) — verify against
 * `patchRecordingUnit_coercesMeasurementFromMap_*` before relying on measurement fields saving correctly.
 */
export function toAnswerInput(value: LocalValue, answerType: FieldAnswerType): AnswerInput {
  if (SELECT_MANY_TYPES.has(answerType)) {
    const refs = (value as ResourceRef[] | null) ?? [];
    return { values: refs.map((r) => r.resourceId) };
  }
  if (SELECT_ONE_TYPES.has(answerType)) {
    const ref = value as ResourceRef | null;
    return { value: ref ? ref.resourceId : null };
  }
  if (answerType === "MEASUREMENT") {
    const m = value as MeasurementRef | null;
    if (!m) return { value: null };
    return { value: { value: m.numericValue ?? null, unit: m.symbol ?? null, comment: m.comment ?? null } };
  }
  // TEXT, DECIMAL, INTEGER, DATETIME: plain scalar passthrough.
  return { value: value ?? null };
}
