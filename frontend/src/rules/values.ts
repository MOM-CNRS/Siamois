// Normalisation of answer values as the API serves them (already unwrapped from their FieldAnswer
// envelope) into comparable scalars. Shape-based, so the same code reads a fiche, a list row or an
// offline record:
// - a reference / concept ({resourceId} or {id})  → its id, as a string
// - a measurement ({numericValue})                → the number
// - an ISO date string ("2024-01-15", "…T00:00:00Z") → epoch ms (a date-only value is read as UTC
//   midnight, which is exactly how the server stores and sends it back)
// - a multi-valued answer (array)                 → an array of the above

import type { FieldValueSpec } from "./types";

export type Scalar = string | number | boolean;

const ISO_DATE = /^\d{4}-\d{2}-\d{2}(?:[T ][\d:.]+(?:Z|[+-]\d{2}:?\d{2})?)?$/;

function scalarOf(value: unknown): Scalar | null {
  if (value == null) return null;
  if (typeof value === "number" || typeof value === "boolean") return value;
  if (typeof value === "string") {
    if (value === "") return null;
    if (ISO_DATE.test(value)) {
      const ms = Date.parse(value.length === 10 ? `${value}T00:00:00Z` : value);
      if (!Number.isNaN(ms)) return ms;
    }
    return value;
  }
  if (typeof value === "object") {
    const o = value as Record<string, unknown>;
    if (o.resourceId != null) return String(o.resourceId);
    if (o.conceptId != null) return String(o.conceptId);
    if (o.id != null) return String(o.id);
    if ("numericValue" in o) return typeof o.numericValue === "number" ? o.numericValue : null;
  }
  return null;
}

/** Every non-empty scalar a value holds (0 for empty, several for a multi-valued answer). */
export function scalarsOf(value: unknown): Scalar[] {
  if (Array.isArray(value)) {
    return value.map(scalarOf).filter((s): s is Scalar => s != null);
  }
  const s = scalarOf(value);
  return s == null ? [] : [s];
}

export function isEmptyValue(value: unknown): boolean {
  return scalarsOf(value).length === 0;
}

/** The single comparable number of a value (number, measurement, date), or null. */
export function numberOf(value: unknown): number | null {
  const [first] = scalarsOf(value);
  return typeof first === "number" ? first : null;
}

/** The single id / scalar of a value as a string, or null. */
export function idOf(value: unknown): string | null {
  const [first] = scalarsOf(value);
  return first == null ? null : String(first);
}

/** The scalar an expected value compares as. */
export function expectedScalar(spec: FieldValueSpec): Scalar | null {
  if (spec != null && typeof spec === "object") {
    if ("conceptId" in spec) return String(spec.conceptId);
    if ("id" in spec) return String(spec.id);
    return null;
  }
  return scalarOf(spec);
}

export function sameScalar(a: Scalar, b: Scalar): boolean {
  if (typeof a === "number" && typeof b === "number") return a === b;
  return String(a) === String(b);
}
