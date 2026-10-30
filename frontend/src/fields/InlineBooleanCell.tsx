import {useState} from "react";
import {BooleanRenderer} from "./renderers";
import {type AnswerInputBody, type FieldResource, toAnswerInput} from "./types";
import {messageForError} from "../api/errors";
import {t} from "../i18n";
import {useToastOnChange} from "../notify/NotifyProvider";

/**
 * A yes/no field edited in place: the checkbox itself is the control, so a click toggles and saves
 * the value at once — no overlay on top of the cell, unlike every other field type. The checkbox
 * shows the new state straight away and goes back to the stored one if the save fails.
 */
export function InlineBooleanCell({ field, rowId, value, readOnly, onSave, onSaved }: {
  field: FieldResource;
  rowId: string | number;
  value: unknown;
  readOnly: boolean;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>, value?: unknown) => Promise<unknown>;
  onSaved: () => void;
}) {
  const [pending, setPending] = useState<boolean | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  useToastOnChange(error);

  const shown = pending !== undefined ? pending : typeof value === "boolean" ? value : null;

  async function commit(next: unknown) {
    const bool = typeof next === "boolean" ? next : null;
    setPending(bool);
    setError(null);
    try {
      await onSave(rowId, { [field.id]: toAnswerInput(field, bool) }, bool);
      onSaved();
    } catch (e) {
      setError(messageForError(e, t("cell.saveFailed")));
    } finally {
      setPending(undefined);
    }
  }

  return (
    // The click is the toggle: it must not also open the row (list) or an editor.
    <span className="inline-boolean-cell" onClick={(e) => e.stopPropagation()}>
      <BooleanRenderer field={field} value={shown} readOnly={readOnly || pending !== undefined} required={false} onChange={commit} />
    </span>
  );
}
