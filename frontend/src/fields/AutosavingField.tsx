import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "primereact/button";
import { Menu } from "primereact/menu";
import { Tooltip } from "primereact/tooltip";
import { ApiError } from "../api/client";
import { commitsImmediately, sameValue } from "./commit";
import { getFieldRenderer } from "./registry";
import type { FieldResource } from "./types";

// One form field that persists itself — a fiche's equivalent of the list's CellEditOverlay,
// without the overlay: the widget is always on screen (disabled outside write mode), so there is
// nothing to open or close, only a commit boundary to pick. Those boundaries come from
// fields/commit.ts, shared with that editor.
//
// Entity-agnostic (plan §8 follow-up): originally lived inside entities/project/FicheTab.tsx, but
// nothing here is Project-shaped — `onSave` is the only thing that knows which entity it's
// writing to. Extracted here so entities/recordingUnit/FicheTab.tsx (and any future schema-driven
// fiche) reuses the exact same commit/error/dirty-state behavior rather than a second copy that
// can silently drift from bug fixes made to one but not the other.
//
// <p>Escape reverts to the stored value. A failed save keeps the edit on screen with the server's
// own message (in particular a 409 on a duplicate identifier and a 400 on a bad number), rather
// than silently discarding it.</p>
export function AutosavingField({
  field,
  stored,
  readOnly,
  required,
  organizationId,
  onSave,
}: {
  field: FieldResource;
  stored: unknown;
  readOnly: boolean;
  required: boolean;
  organizationId?: number;
  onSave: (field: FieldResource, value: unknown) => Promise<void>;
}) {
  const [draft, setDraft] = useState<unknown>(stored);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const draftRef = useRef<unknown>(stored);
  draftRef.current = draft;
  // The last value successfully persisted, which is what "unchanged" compares against — not
  // `stored`, which only catches up on the next refetch.
  const baselineRef = useRef<unknown>(stored);
  const savingRef = useRef(false);

  // A change from elsewhere (the panel refetching after another field's save) moves the baseline
  // with it, so this field doesn't then re-send a value it never owned.
  useEffect(() => {
    if (!savingRef.current && sameValue(draftRef.current, baselineRef.current)) {
      baselineRef.current = stored;
      setDraft(stored);
    }
  }, [stored]);

  const commit = useCallback(
    async (value: unknown) => {
      if (sameValue(value, baselineRef.current)) return;
      if (savingRef.current) return;
      // p:outputLabel indicateRequired + required="#{col.required}" is what stops this in JSF, on
      // the ajax submit that leaves the field. Clearing a required field has no valid target
      // value, so it is refused here rather than sent for the server to reject.
      if (required && isEmptyValue(value)) {
        setError("Ce champ est obligatoire");
        return;
      }
      savingRef.current = true;
      setSaving(true);
      setError(null);
      try {
        await onSave(field, value);
        baselineRef.current = value;
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "La modification a échoué.");
      } finally {
        savingRef.current = false;
        setSaving(false);
      }
    },
    [field, onSave, required],
  );

  const immediate = commitsImmediately(field);

  function onChange(value: unknown) {
    setDraft(value);
    // A picker or a date yields one final value per interaction, so there is no "still typing"
    // state to wait through. A text or number input does — it commits on blur or Enter below.
    if (immediate) void commit(value);
  }

  function onBlur() {
    if (!immediate) void commit(draftRef.current);
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      setDraft(baselineRef.current);
      setError(null);
    } else if (e.key === "Enter" && !immediate) {
      e.preventDefault();
      void commit(draftRef.current);
    }
  }

  const Renderer = getFieldRenderer(field.answerType);
  const dirty = !sameValue(draft, stored);

  return (
    // panelField.xhtml's field-value-group (+ ui-fluid, which it drops only for the spatial tree
    // picker) wrapping a label row and the widget below it.
    <div
      className={`field-value-group${field.answerType === "SELECT_MULTIPLE_SPATIAL_UNIT_TREE" ? "" : " ui-fluid"}${
        dirty ? " sia-modified-field" : ""
      }${saving ? " sia-field-saving" : ""}`}
      onBlur={onBlur}
      onKeyDown={onKeyDown}
    >
      <FieldLabel field={field} required={required} />
      <div>
        <Renderer
          field={field}
          value={draft}
          readOnly={readOnly || saving}
          required={required}
          organizationId={organizationId}
          onChange={onChange}
        />
      </div>
      {error && <div className="field-value-error">{error}</div>}
    </div>
  );
}

/**
 * panelField.xhtml's header-toggle-container: the flat label button — the field's own icon
 * (CustomField.getIcon(), per type: "bi bi-alphabet" for TEXT, "bi bi-calendar" for DATETIME,
 * "bi bi-geo-alt" for a place, "sia-icon-opentheso" for a vocabulary field) plus the title,
 * ellipsised and marked with a "*" when required — with a tooltip carrying the full title, and an
 * overlay menu whose one applicable item links to the field's concept in the thesaurus.
 *
 * <p>Two things here are less obvious than they look:</p>
 * <ul>
 *   <li><strong>p-button-text, not p-button-flat.</strong> "flat" is PrimeFaces' name for this
 *   variant (.ui-button-flat); PrimeReact calls it .p-button-text and has no .p-button-flat at all.
 *   Using the wrong one left the button with lara's solid blue fill under a grey label — an
 *   unreadable field header. The SIAMOIS flat look is then restyled on top, in main-panel.css.</li>
 *   <li><strong>appendTo={document.body}</strong> pins where the popup lands. It is also
 *   PrimeReact's default (Portal falls back to document.body when appendTo is unset), but that
 *   default is overridable app-wide through PrimeReact.appendTo, and this menu must not end up
 *   inside the fiche: the fiche sits in the panel's own overflow:auto box, which would clip it.
 *   JSF's own p:menu is overlay="true" for the same reason, and CellEditOverlay portals to the
 *   body for the same reason again.</li>
 * </ul>
 *
 * <p>NOTE on "the menu does not open": if that is what you see in the running app, check the API
 * before this component. The menu exists only when FieldResource carries a conceptUri, and both
 * that property and `icon` were added to FieldResource recently — a server build predating them
 * serves neither, which shows up as a label with no icon AND a click that does nothing. Verify
 * with GET /api/v1/organizations/{id}/project-types (or the recording-unit-types equivalent).</p>
 *
 * <p>The menu is rendered only when the field HAS a conceptUri: JSF always renders it, but its
 * sole non-conditional item is that link, so a menu without one would open onto nothing. The
 * button then presents itself as non-interactive (no aria-haspopup, default cursor) rather than
 * inviting a click that does nothing.</p>
 */
export function FieldLabel({ field, required }: { field: FieldResource; required: boolean }) {
  const menuRef = useRef<Menu>(null);
  const buttonId = `sia-field-label-${field.id}`;
  const hasThesaurusEntry = Boolean(field.conceptUri);

  return (
    <div className="header-toggle-container">
      <Tooltip target={`#${buttonId}`} content={field.label} position="left" />
      <Button
        id={buttonId}
        type="button"
        className={`p-button-text ellipsis-btn${required ? " required-btn" : ""}${
          hasThesaurusEntry ? " has-thesaurus-entry" : ""
        }`}
        icon={field.icon ?? undefined}
        label={field.label}
        onClick={(e) => menuRef.current?.toggle(e)}
        aria-haspopup={hasThesaurusEntry ? "menu" : undefined}
      />
      {hasThesaurusEntry && (
        <Menu
          ref={menuRef}
          popup
          appendTo={document.body}
          model={[
            {
              label: "Voir dans le thésaurus",
              icon: "bi bi-info-circle",
              url: field.conceptUri as string,
              target: "_blank",
            },
          ]}
        />
      )}
      {field.hint && <small className="field-hint">{field.hint}</small>}
    </div>
  );
}

export function isEmptyValue(value: unknown): boolean {
  if (value == null) return true;
  if (Array.isArray(value)) return value.length === 0;
  if (typeof value === "string") return value.trim().length === 0;
  return false;
}
