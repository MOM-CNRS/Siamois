import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { ZIndexUtils } from "primereact/utils";
import { commitsImmediately, sameValue, staysOpenAfterSave } from "../../fields/commit";
import { renderAnswerCell } from "../../fields/display";
import { editContextOf } from "../../fields/editContext";
import { isEmptyValue } from "../../fields/FieldLabel";
import { getFieldRenderer } from "../../fields/registry";
import { fetchAllValues } from "../../fields/multiValues";
import {
  readMultiValue,
  resolveValueBinding,
  toAnswerInput,
  type AnswerInputBody,
  type FieldResource,
} from "../../fields/types";
import { numberOf, type Bound, type FieldState } from "../../rules";
import { messageForError } from "../../api/errors";
import { t } from "../../i18n";

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
  // A layout-level property (FormLayoutCol.isRequired, not on FieldResource itself), so it travels
  // with the target rather than the field — the fiche's own click-to-edit fields set it; a table
  // column, which has no per-cell required flag today, leaves it unset (same as before this
  // existed: no required guard at all).
  required?: boolean;
  // Show the value rather than an editor: a read-only field, a type with no editor, or a row the
  // user can't edit. The overlay opens all the same — a cell's first click always does — and its
  // chips are the links that open the referenced fiches.
  readOnly?: boolean;
  // The field's state under the form's rules, when edited inside a form that has them: its bounds
  // are checked before saving, its options context reaches the picker.
  fieldState?: FieldState;
  // Why the stored value no longer fits the rules, in words (fields/incoherence.ts) — shown above
  // the editor, with the one way out: « Vider ».
  incoherences?: string[];
  // The field could be edited but for the rules (disabled): clearing it is still allowed.
  canClear?: boolean;
  // Names the other field a bound comes from in the error message.
  fieldLabelOf?: (fieldId: string) => string;
}

export interface CellEditOverlayProps<TRow extends { id?: string | number }> {
  target: CellEditTarget<TRow> | null;
  organizationId?: number;
  // Registry key of the edited rows' entity type — with the row, what tells a relation picker
  // which project to search and what a « Nouveau » from it is linked to (fields/editContext.ts).
  entityType?: string;
  // `value` is the field's whole new value, for a caller that can't write an add/remove difference
  // (the Project fiche's spatial context, written through its flat alias).
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>, value?: unknown) => Promise<unknown>;
  // Called after a successful save so the caller can invalidate/refetch (the overlay itself
  // doesn't know about React Query).
  onSaved: () => void;
  onClose: () => void;
}

// Matches p-connected-overlay-enter / -enter-active / -enter-done / -exit…: the CSSTransition
// class names every PrimeReact connected popup (AutoComplete, Calendar, Dropdown, MultiSelect, …)
// puts on its portalled root.
// A reference picker's « Nouveau » opens the creation form in a PrimeReact OverlayPanel (portalled
// too): the creation happens inside the edit, not after it.
const PRIMEREACT_POPUP_SELECTOR = '[class*="p-connected-overlay"], .p-overlaypanel, .p-dialog-mask';

export function CellEditOverlay<TRow extends { id?: string | number }>({
  target,
  organizationId,
  entityType,
  onSave,
  onSaved,
  onClose,
}: CellEditOverlayProps<TRow>) {
  const [draft, setDraft] = useState<unknown>(undefined);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // A multi-valued answer that was only a preview (MultiValueAnswer.complete false) is fetched
  // whole before it can be edited: the picker must show every value to let one be removed.
  const [loadingValues, setLoadingValues] = useState(false);
  const loadingRef = useRef(false);
  loadingRef.current = loadingValues;
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
    setLoadingValues(target != null && partialAnswerOf(target) != null);
  }

  useEffect(() => {
    const partial = seededTarget ? partialAnswerOf(seededTarget) : null;
    if (!partial) return;
    let cancelled = false;
    fetchAllValues(partial)
      .then((all) => {
        if (cancelled) return;
        initialRef.current = all;
        setDraft(all);
      })
      .catch((e) => {
        if (!cancelled) setError(messageForError(e, t("cell.loadFailed")));
      })
      .finally(() => {
        if (!cancelled) setLoadingValues(false);
      });
    return () => {
      cancelled = true;
    };
  }, [seededTarget]);

  const save = useCallback(
    async (value: unknown): Promise<boolean> => {
      if (!target || target.row.id == null) return false;
      // Nothing to save before the whole value is known — and nothing was edited either. Nor
      // ever from a read-only overlay.
      if (loadingRef.current || target.readOnly) return true;
      if (sameValue(value, initialRef.current)) return true;
      if (savingRef.current) return false;
      // p:outputLabel indicateRequired + required="#{col.required}" is what stops this in JSF, on
      // the ajax submit that leaves the field. Clearing a required field has no valid target
      // value, so it is refused here rather than sent for the server to reject.
      if (target.required && isEmptyValue(value)) {
        setError(t("cell.required"));
        return false;
      }
      const outOfBounds = boundViolation(value, target.fieldState, target.fieldLabelOf);
      if (outOfBounds) {
        setError(outOfBounds);
        return false;
      }
      savingRef.current = true;
      setSaving(true);
      setError(null);
      try {
        // A multi-valued field is written as what changed since the editor opened (add/remove),
        // never as a whole list: other clients may have added values since, and the server keeps them.
        // An answer the row didn't carry at all counts as empty: only what was picked is added.
        if (target.field.answerType.startsWith("SELECT_MULTIPLE")) {
          const input = toAnswerInput(target.field, value, initialRef.current ?? []);
          await onSave(target.row.id, { [target.field.id]: input }, value);
        } else {
          await onSave(target.row.id, { [target.field.id]: toAnswerInput(target.field, value) });
        }
        initialRef.current = value;
        onSaved();
        return true;
      } catch (e) {
        // Surfaces ProjectApiService's own message inline — in particular the 409 from a duplicate
        // identifier (ActionUnitAlreadyExistsException), matching JSF's handleLinkEdit validation.
        setError(messageForError(e, t("cell.saveFailed")));
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

  // « Vider »: the one edit an incoherent value always allows — even on a field the rules disabled.
  const clear = useCallback(async () => {
    if (!target || target.row.id == null || savingRef.current) return;
    if (target.required) {
      setError(t("cell.required"));
      return;
    }
    const multiple = target.field.answerType.startsWith("SELECT_MULTIPLE");
    savingRef.current = true;
    setSaving(true);
    setError(null);
    try {
      await onSave(target.row.id, { [target.field.id]: multiple ? { values: [] } : { value: null } }, multiple ? [] : null);
      onSaved();
      onClose();
    } catch (e) {
      setError(messageForError(e, t("cell.saveFailed")));
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }, [target, onSave, onSaved, onClose]);

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
  //
  // Not before a preview's whole list has loaded: the widget isn't there yet, so the focus (and
  // with it the picker's first search) waits for it.
  useEffect(() => {
    if (!target || loadingValues) return;
    const control = boxRef.current?.querySelector<HTMLElement>("input, textarea, select");
    if (!control) return;
    control.focus();
    if (control instanceof HTMLInputElement && control.type !== "checkbox" && control.type !== "radio") {
      control.select();
    } else if (control instanceof HTMLTextAreaElement) {
      control.select();
    }
  }, [target, loadingValues]);

  // Joins PrimeReact's own z-index stack (at 1200 or above, as its CSS used to fix it) rather than
  // sitting at a fixed value: every popup opened from inside the edit afterwards — the picker's
  // suggestions, a « Nouveau » creation overlay and that form's own dropdowns — then stacks above
  // it, in the order it was opened.
  const hasTarget = target != null;
  useLayoutEffect(() => {
    const box = boxRef.current;
    if (!hasTarget || !box) return;
    ZIndexUtils.set("overlay", box, true, 1200);
    return () => ZIndexUtils.clear(box);
  }, [hasTarget]);

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
      {target.incoherences && target.incoherences.length > 0 && (
        <div className="cell-edit-overlay-incoherent" role="alert">
          <i className="bi bi-exclamation-triangle-fill" aria-hidden />
          <div className="cell-edit-overlay-incoherent-text">
            {target.incoherences.map((text) => (
              <div key={text}>{text}</div>
            ))}
          </div>
          {target.canClear && !isEmptyValue(draft) && (
            <button type="button" className="cell-edit-overlay-clear" disabled={saving} onClick={() => void clear()}>
              {t("cell.clear")}
            </button>
          )}
        </div>
      )}
      {loadingValues ? (
        <div className="cell-edit-overlay-loading">{t("common.loadingValues")}</div>
      ) : target.readOnly ? (
        <div className="cell-edit-overlay-readonly">
          {/* A chip opens its fiche in the overview; the overlay's job is done then. */}
          {renderAnswerCell(field, draft, { all: true, onOpenLink: onClose }) || (
            <span className="cell-edit-overlay-empty">Aucune valeur</span>
          )}
        </div>
      ) : (
        <Renderer
          field={field}
          value={draft}
          readOnly={saving}
          required={target.required ?? false}
          organizationId={organizationId}
          context={editContextOf(target.row, entityType, organizationId)}
          bounds={target.fieldState?.bounds}
          optionsContext={target.fieldState?.optionsContext}
          onChange={onValueChange}
        />
      )}
      {error && <div className="cell-edit-overlay-error">{error}</div>}
    </div>,
    document.body,
  );
}

/** The target's answer when it is a multi-valued one the row only carries a preview of. */
function partialAnswerOf<TRow>(target: CellEditTarget<TRow>) {
  if (!target.field.answerType.startsWith("SELECT_MULTIPLE")) return null;
  const answer = readMultiValue(resolveValueBinding(target.field).readRaw(target.row));
  return answer && !answer.complete && answer._links?.values ? answer : null;
}

const BOUND_KEYS = {
  min: ["cell.boundMinIncl", "cell.boundMinExcl"],
  max: ["cell.boundMaxIncl", "cell.boundMaxExcl"],
} as const;

/** The message for a value outside the bounds the rules put on it, or null when it fits (or is empty). */
function boundViolation(
  value: unknown,
  state: FieldState | undefined,
  labelOf: (fieldId: string) => string = (id) => id,
): string | null {
  const bounds = state?.bounds;
  const n = numberOf(value);
  if (!bounds || n == null) return null;
  const check = (side: "min" | "max", bound: Bound | undefined) => {
    if (!bound) return null;
    const ok =
      side === "min"
        ? bound.exclusive ? n > bound.value : n >= bound.value
        : bound.exclusive ? n < bound.value : n <= bound.value;
    return ok ? null : t(BOUND_KEYS[side][bound.exclusive ? 1 : 0], { label: labelOf(bound.fieldId) });
  };
  return check("min", bounds.min) ?? check("max", bounds.max);
}
