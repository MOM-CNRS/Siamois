import { useRef } from "react";
import { Button } from "primereact/button";
import { Menu } from "primereact/menu";
import { Tooltip } from "primereact/tooltip";
import type { FieldResource } from "./types";

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
