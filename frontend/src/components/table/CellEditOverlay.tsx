import { useCallback, useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { commitsImmediately, sameValue, staysOpenAfterSave } from "../../fields/commit";
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

// Matches p-connected-overlay-enter / -enter-active / -enter-done / -exit…: the CSSTransition
// class names every PrimeReact connected popup (AutoComplete, Calendar, Dropdown, MultiSelect, …)
// puts on its portalled root.
const PRIMEREACT_POPUP_SELECTOR = '[class*="p-connected-overlay"]';

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
  // Escape only: the one path that must discard the edit instead of committing it.
  const cancelledRef = useRef(false);
  // Guards against two saves overlapping (e.g. a value change and an outside click landing on the
  // same tick). Deliberately a ref, not the `saving` state: it has to be readable synchronously.
  const savingRef = useRef(false);

  // Seeded during render rather than in an effect (React's "adjust state when a prop changes"
  // pattern): an effect would seed the draft only after the first commit, so the focus effect
  // below would select an input that is still empty, and the value would land in it afterwards
  // with the caret at the end.
  const [seededTarget, setSeededTarget] = useState<CellEditTarget<TRow> | null>(null);
  if (target !== seededTarget) {
    const current = target ? resolveValueBinding(target.field).read(target.row) : undefined;
    initialRef.current = current;
    cancelledRef.current = false;
    setSeededTarget(target);
    setDraft(current);
    setError(null);
  }

  const save = useCallback(
    async (value: unknown): Promise<boolean> => {
      if (!target || target.row.id == null) return false;
      if (sameValue(value, initialRef.current)) return true;
      if (savingRef.current) return false;
      savingRef.current = true;
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
        return false;
      } finally {
        savingRef.current = false;
        setSaving(false);
      }
    },
    [target, onSave, onSaved],
  );

  const saveAndClose = useCallback(
    async (value: unknown) => {
      if (cancelledRef.current) return;
      if (await save(value)) onClose();
    },
    [save, onClose],
  );

  // The editor opens on a click, so the user's hands are already on the mouse and their intent is
  // already "change this value" — landing them on a box they still have to click into is a wasted
  // step. Focusing the widget's own control also starts an autocomplete picker's initial search
  // (fields/renderers.tsx searches on focus), so the options are on screen before anything is
  // typed. Text is selected so typing replaces the current value, matching what JSF's own
  // identifier edit does (entityDataTable.xhtml: .trigger('focus').trigger('select')).
  //
  // Deliberately done by reaching into the DOM rather than by passing an `autoFocus` prop through
  // FieldRendererProps: renderers are a registry any module can add to, and this way a renderer
  // written later gets the behaviour without knowing about it.
  useEffect(() => {
    if (!target) return;
    const control = boxRef.current?.querySelector<HTMLElement>("input, textarea, select");
    if (!control) return;
    control.focus();
    if (control instanceof HTMLInputElement && control.type !== "checkbox" && control.type !== "radio") {
      control.select();
    } else if (control instanceof HTMLTextAreaElement) {
      control.select();
    }
  }, [target]);

  // Focus leaving the overlay (a click elsewhere, Tab) is what "the edit is finished" means for a
  // text/number widget. Mousedown rather than click: a click on another cell would otherwise open
  // that cell's editor before this one had a chance to commit.
  const draftRef = useRef<unknown>(undefined);
  draftRef.current = draft;
  useEffect(() => {
    if (!target) return;
    function onPointerDown(e: globalThis.MouseEvent) {
      const box = boxRef.current;
      const clicked = e.target instanceof Element ? e.target : null;
      if (!box || !clicked || box.contains(clicked)) return;
      // A widget's own popup (the autocomplete suggestion list, the date picker) is portalled to
      // document.body, so it is "outside" this box by DOM containment while being very much part
      // of the edit in progress. Without this, picking a suggestion is an outside mousedown: the
      // editor closes before the click reaches the item, and nothing is ever saved. Every
      // PrimeReact popup shares the p-connected-overlay transition classes, so one check covers
      // them all — including widgets from renderers added to the registry later.
      if (clicked.closest(PRIMEREACT_POPUP_SELECTOR)) return;
      void saveAndClose(draftRef.current);
    }
    document.addEventListener("mousedown", onPointerDown, true);
    return () => document.removeEventListener("mousedown", onPointerDown, true);
  }, [target, saveAndClose]);

  if (!target) return null;

  const { field, anchor } = target;
  const Renderer = getFieldRenderer(field.answerType);

  // fields/commit.ts owns both rules, shared with the Project fiche's own autosave: save on change
  // for a widget whose one interaction yields one final value, and for a multi-valued one keep the
  // editor open afterwards so the next pick doesn't need a reopen.
  const immediate = commitsImmediately(field);
  const keepOpen = staysOpenAfterSave(field);

  function onValueChange(value: unknown) {
    setDraft(value);
    if (!immediate) return;
    if (keepOpen) void save(value);
    else void saveAndClose(value);
  }

  function onKeyDown(e: ReactKeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      cancelledRef.current = true;
      onClose();
    } else if (e.key === "Enter" && !immediate) {
      e.preventDefault();
      void saveAndClose(draft);
    }
  }

  return createPortal(
    <div
      ref={boxRef}
      // p-fluid is PrimeReact's own "inputs fill their container" class: it covers every widget,
      // including ones a renderer added to the registry later would use, which a hand-written list
      // of width rules per component would not.
      className={`cell-edit-overlay p-fluid${saving ? " cell-edit-overlay-saving" : ""}`}
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
