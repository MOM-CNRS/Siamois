// Read-only cell formatting for a dynamic (schema-driven) list column. Deliberately separate from
// fields/renderers.tsx: those are full PrimeReact edit-mode widgets meant for a form or an edit
// overlay, which is far more machinery than a table cell needs. A cell just needs a string.
import type { ReactNode } from "react";
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
  // A null inside a multi-valued answer would otherwise print the string "null" and, worse, count
  // towards the "+N" — the array filters on label length, not on the raw item.
  if (value == null) return "";
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

/**
 * Cell rendering for a projected answer — the display counterpart of {@link renderAnswerValue},
 * which stays the plain-text version (tooltips, tests, anything that needs a string).
 *
 * <p>A multi-valued answer (SELECT_MULTIPLE_*) is shown as its first label plus a {@code +N}
 * count, never as the full comma-joined list: a row is one line tall, so a joined list is just a
 * long string that gets cut mid-label, which reads as a truncated single value rather than as
 * "there are others". The full list stays available as the counter's tooltip.</p>
 */
export function renderAnswerCell(field: FieldResource, rawValue: unknown): ReactNode {
  const value = unwrapAnswer(rawValue);
  if (value == null) return "";
  if (!Array.isArray(value)) return formatOne(field, value);

  const labels = value.map((item) => formatOne(field, item)).filter((s) => s.length > 0);
  if (labels.length === 0) return "";
  if (labels.length === 1) return labels[0];
  return (
    <span className="cell-multi">
      <span className="cell-multi-first">{labels[0]}</span>
      <span className="cell-multi-more" title={labels.join(", ")}>
        +{labels.length - 1}
      </span>
    </span>
  );
}
