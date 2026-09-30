import type { ReactNode } from "react";
import { Button } from "primereact/button";
import { Message } from "primereact/message";
import { getEntityType } from "../entities/registry";
import { entityChipStyle } from "../fields/display";

/**
 * The frame every entity's create form shares — JSF's newUnitDialog.xhtml, as an overlay: a header
 * with the entity's icon and title in its colour, the fields, then a light footer with « Annuler »
 * and « Créer ». The entity's colour is set once here (--entity-chip-color); the header, the
 * overlay's top edge and the Créer button all take it.
 */
export function CreateFormShell({
  entityType,
  title,
  canSubmit,
  submitLabel = "Créer",
  pending,
  error,
  footerNote,
  onSubmit,
  onCancel,
  children,
}: {
  // Registry key: gives the icon and the colour.
  entityType: string;
  title: string;
  canSubmit: boolean;
  // The confirm button's label — « Créer » unless the form does something else (a duplication).
  submitLabel?: string;
  pending: boolean;
  error?: string | null;
  // Shown at the footer's left, before the buttons (what the form is about to do).
  footerNote?: ReactNode;
  onSubmit: () => void;
  onCancel: () => void;
  children: ReactNode;
}) {
  const icon = getEntityType(entityType)?.icon;
  return (
    <form
      className="create-form"
      style={entityChipStyle(entityType)}
      onSubmit={(e) => {
        e.preventDefault();
        if (canSubmit) onSubmit();
      }}
    >
      <div className="create-form-header">
        {icon && <i className={icon} aria-hidden="true" />}
        <h4>{title}</h4>
      </div>

      <div className="create-form-body">
        {children}
        {error && <Message severity="error" text={error} />}
      </div>

      <div className="create-form-footer">
        {footerNote && <span className="create-form-footer-note">{footerNote}</span>}
        <Button type="button" label="Annuler" text onClick={onCancel} />
        <Button type="submit" label={submitLabel} disabled={!canSubmit} loading={pending} />
      </div>
    </form>
  );
}

/** One field of a create form: its label above it, like a fiche field's; "*" when required. */
export function CreateFormField({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) {
  return (
    <label className="project-create-form-field">
      <span className={required ? "create-form-label create-form-label-required" : "create-form-label"}>{label}</span>
      {children}
    </label>
  );
}
