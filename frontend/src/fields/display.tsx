// Read-only cell formatting for a dynamic (schema-driven) list column. Deliberately separate from
// fields/renderers.tsx: those are full PrimeReact edit-mode widgets meant for a form or an edit
// overlay, which is far more machinery than a table cell needs. A cell just needs a string.
import type { FieldResource } from "./types";
import { unwrapAnswer } from "./types";

interface ResourceRefLike {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

function isResourceRef(value: unknown): value is ResourceRefLike {
  return value != null && typeof value === "object" && "resourceId" in value;
}

function formatOne(field: FieldResource, value: unknown): string {
  if (isResourceRef(value)) {
    return value.label ?? value.resourceId;
  }
  if (field.answerType === "DATETIME" && typeof value === "string") {
    return value.slice(0, 10);
  }
  if (typeof value === "number") {
    return String(value);
  }
  return String(value);
}

/**
 * Formats a projected `answers[fieldId]` value for a read-only table cell. Accepts either the raw
 * value ProjectAnswersProjector emits on list rows or a FieldAnswer envelope (unwrapped the same
 * way resolveValueBinding does), so callers can pass either straight through.
 */
export function renderAnswerValue(field: FieldResource, rawValue: unknown): string {
  const value = unwrapAnswer(rawValue);
  if (value == null) return "";
  if (Array.isArray(value)) {
    return value
        .map((item) => formatOne(field, item))
        .filter((s) => s.length > 0)
        .join(", ");
  }
  return formatOne(field, value);
}
