import type { FieldResource } from "./types";

/**
 * When a field widget's value counts as "the user is done editing" — the rules the table's cell
 * editor (components/table/CellEditOverlay.tsx) worked out, extracted so the Project fiche
 * (entities/project/FicheTab.tsx) commits on exactly the same boundaries rather than inventing a
 * second, subtly different set. Both surfaces autosave with no Save button, so "when do we PATCH?"
 * is the one question they have to answer identically.
 */

/**
 * True when one interaction produces one final value, so there is no "still typing" state to wait
 * through: a picker or a date widget can be saved the instant it changes. A text or number input
 * cannot — that would be one PATCH per keystroke.
 */
export function commitsImmediately(field: FieldResource): boolean {
  return field.answerType.startsWith("SELECT_") || field.answerType === "DATETIME";
}

/**
 * True for a multi-valued field, where one pick is not the whole answer: each pick is saved as it
 * happens, but the editor must stay open so the next one doesn't need a reopen.
 */
export function staysOpenAfterSave(field: FieldResource): boolean {
  return field.answerType.startsWith("SELECT_MULTIPLE");
}

/**
 * Whether a value is unchanged from the one the editor opened with — the check that keeps an
 * untouched field from issuing a PATCH at all. Structural, because SELECT_* values are objects
 * (ResourceRef) and arrays that are rebuilt on every render.
 */
export function sameValue(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (a == null && b == null) return true;
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
}
