import { useEffect, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { Button } from "primereact/button";
import { getFieldRenderer } from "../../fields/registry";
import { resolveValueBinding, toAnswerInput, type AnswerInputBody, type FieldResource } from "../../fields/types";
import { ApiError } from "../../api/client";

/**
 * One shared edit surface for every editable cell in the list — anchored to the clicked cell and
 * initialized from `(row, field)`, per the plan's "one edit overlay for all the fields" design
 * (deliberately not a pencil button or per-cell inline widget). Owned by EntityListPanel, which
 * holds the single {@link OverlayPanel} ref and calls `show`/`hide` on it; this component only
 * needs the target and how to save.
 */
export interface CellEditTarget<TRow> {
  row: TRow;
  field: FieldResource;
}

export interface CellEditOverlayProps<TRow extends { id?: string | number }> {
  target: CellEditTarget<TRow> | null;
  organizationId?: number;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  // Called after a successful save so the caller can invalidate/refetch (the overlay itself
  // doesn't know about React Query).
  onSaved: () => void;
  onClose: () => void;
}

export function CellEditOverlay<TRow extends { id?: string | number }>({
  target,
  organizationId,
  onSave,
  onSaved,
  onClose,
}: CellEditOverlayProps<TRow>) {
  const [draft, setDraft] = useState<unknown>(undefined);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Re-seeds the draft from the freshly-clicked (row, field) every time the target changes —
  // this component is a single shared instance, not remounted per cell.
  useEffect(() => {
    if (!target) return;
    const binding = resolveValueBinding(target.field);
    setDraft(binding.read(target.row));
    setError(null);
  }, [target]);

  if (!target) return null;

  const { row, field } = target;
  const Renderer = getFieldRenderer(field.answerType);

  async function save() {
    if (!target || row.id == null) return;
    setSaving(true);
    setError(null);
    try {
      const input = toAnswerInput(field, draft);
      await onSave(row.id, { [field.id]: input });
      onSaved();
      onClose();
    } catch (e) {
      // Surfaces ProjectApiService's own message inline — in particular the 409 from a duplicate
      // identifier (ActionUnitAlreadyExistsException), matching JSF's handleLinkEdit validation.
      setError(e instanceof ApiError ? e.message : "La modification a échoué.");
    } finally {
      setSaving(false);
    }
  }

  // Enter saves for scalar fields (TEXT/INTEGER/DECIMAL/DATETIME); SELECT_* renderers use Enter
  // to confirm a suggestion in their own AutoComplete, so wiring it here too would double-fire.
  // Escape always cancels — it isn't otherwise claimed by any renderer.
  const enterSaves = !field.answerType.startsWith("SELECT_");

  function onKeyDown(e: ReactKeyboardEvent) {
    if (e.key === "Escape") {
      onClose();
    } else if (e.key === "Enter" && enterSaves) {
      void save();
    }
  }

  return (
    <div className="cell-edit-overlay" onKeyDown={onKeyDown}>
      <label className="cell-edit-overlay-label">{field.label}</label>
      <Renderer
        field={field}
        value={draft}
        readOnly={false}
        required={false}
        organizationId={organizationId}
        onChange={setDraft}
      />
      {error && <div className="cell-edit-overlay-error">{error}</div>}
      <div className="cell-edit-overlay-actions">
        <Button label="Annuler" text onClick={onClose} disabled={saving} />
        <Button label="Enregistrer" onClick={() => void save()} loading={saving} />
      </div>
    </div>
  );
}
