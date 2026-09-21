import { useCallback, useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { getFieldRenderer } from "../../fields/registry";
import { resolveValueBinding, toAnswerInput, type AnswerInputBody, type FieldResource } from "../../fields/types";
import { ApiError } from "../../api/client";

/**
 * One shared edit surface for every editable cell in the list — rendered ON TOP of the clicked
 * cell (same position, same width), showing the field's edit widget and nothing else: no label, no
 * Save/Cancel buttons. Saving happens when the value changes, Notion-style.
 *
 * <p>"On value change" is not "on every keystroke": a SELECT_ or DATETIME widget produces one
 * discrete value per interaction, so it saves and closes immediately; a text/number widget saves
 * once the edit is finished — Enter, or focus leaving the overlay. Saving per keystroke would mean
 * one PATCH and one list refetch per character.</p>
 *
 * <p>Escape cancels without saving. A save that fails keeps the overlay open with the server's own
 * message (in particular the 409 on a duplicate identifier), rather than silently discarding the
 * edit.</p>
 *
 * <p>Positioned by the anchor cell's own client rect rather than by PrimeReact's OverlayPanel:
 * OverlayPanel always drops BELOW its target (with an arrow), which is a popover, not an in-place
 * editor.</p>
 */
export interface CellEditTarget<TRow> {
  row: TRow;
  field: FieldResource;
  // The clicked cell's bounding rect, captured at click time (viewport coordinates — the overlay
  // is position: fixed).
  anchor: DOMRect;
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

function sameValue(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (a == null && b == null) return true;
  return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
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
  const boxRef = useRef<HTMLDivElement>(null);

  // The value the cell held when the overlay opened — the baseline every "did it actually change?"
  // check compares against, so closing an untouched overlay issues no PATCH at all.
  const initialRef = useRef<unknown>(undefined);
  // Set by Escape, and while a save is in flight, so the close paths below don't re-save.
  const skipSaveRef = useRef(false);

  useEffect(() => {
    if (!target) return;
    const binding = resolveValueBinding(target.field);
    const current = binding.read(target.row);
    initialRef.current = current;
    skipSaveRef.current = false;
    setDraft(current);
    setError(null);
  }, [target]);

  const save = useCallback(
    async (value: unknown): Promise<boolean> => {
      if (!target || target.row.id == null) return false;
      if (sameValue(value, initialRef.current)) return true;
      skipSaveRef.current = true;
      setSaving(true);
      setError(null);
      try {
        await onSave(target.row.id, { [target.field.id]: toAnswerInput(target.field, value) });
        initialRef.current = value;
        onSaved();
        return true;
      } catch (e) {
        // Surfaces ProjectApiService's own message inline — in particular the 409 from a duplicate
        // identifier (ActionUnitAlreadyExistsException), matching JSF's handleLinkEdit validation.
        setError(e instanceof ApiError ? e.message : "La modification a échoué.");
        skipSaveRef.current = false;
        return false;
      } finally {
        setSaving(false);
      }
    },
    [target, onSave, onSaved],
  );

  const saveAndClose = useCallback(
    async (value: unknown) => {
      if (skipSaveRef.current) return;
      if (await save(value)) onClose();
    },
    [save, onClose],
  );

  // Focus leaving the overlay (a click elsewhere, Tab) is what "the edit is finished" means for a
  // text/number widget. Mousedown rather than click: a click on another cell would otherwise open
  // that cell's editor before this one had a chance to commit.
  const draftRef = useRef<unknown>(undefined);
  draftRef.current = draft;
  useEffect(() => {
    if (!target) return;
    function onPointerDown(e: globalThis.MouseEvent) {
      const box = boxRef.current;
      if (!box || box.contains(e.target as Node)) return;
      void saveAndClose(draftRef.current);
    }
    document.addEventListener("mousedown", onPointerDown, true);
    return () => document.removeEventListener("mousedown", onPointerDown, true);
  }, [target, saveAndClose]);

  if (!target) return null;

  const { field, anchor } = target;
  const Renderer = getFieldRenderer(field.answerType);

  // One interaction produces one final value for these, so there is no "still typing" state to
  // wait through — save as soon as it changes, which is what the user asked for literally.
  const commitsImmediately = field.answerType.startsWith("SELECT_") || field.answerType === "DATETIME";

  function onValueChange(value: unknown) {
    setDraft(value);
    if (commitsImmediately) void saveAndClose(value);
  }

  function onKeyDown(e: ReactKeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      skipSaveRef.current = true;
      onClose();
    } else if (e.key === "Enter" && !commitsImmediately) {
      e.preventDefault();
      void saveAndClose(draft);
    }
  }

  return createPortal(
    <div
      ref={boxRef}
      className={`cell-edit-overlay${saving ? " cell-edit-overlay-saving" : ""}`}
      style={{
        position: "fixed",
        top: `${anchor.top}px`,
        left: `${anchor.left}px`,
        minWidth: `${Math.max(anchor.width, 12 * 16)}px`,
      }}
      onKeyDown={onKeyDown}
    >
      <Renderer
        field={field}
        value={draft}
        readOnly={saving}
        required={false}
        organizationId={organizationId}
        onChange={onValueChange}
      />
      {error && <div className="cell-edit-overlay-error">{error}</div>}
    </div>,
    document.body,
  );
}
