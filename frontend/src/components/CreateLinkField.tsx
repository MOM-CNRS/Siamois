import { getEntityType } from "../entities/registry";
import type { EntityRef } from "../entities/types";
import { entityChipStyle } from "../fields/display";

// A create form's pre-set link (CreatePrefill) — the entity the new one will be attached to,
// shown read-only where a picker would otherwise be, so the user sees what they are creating.
export function CreateLinkField({ label, entityType, value }: { label: string; entityType: string; value: EntityRef }) {
  return (
    <div className="project-create-form-field create-link-field">
      <span className="create-form-label">{label}</span>
      <span className="entity-nav-chip create-link-field-value" style={entityChipStyle(entityType)}>
        <i className={getEntityType(entityType)?.icon ?? "bi bi-link"} aria-hidden="true" />
        <span className="entity-nav-chip-label">{value.label}</span>
      </span>
    </div>
  );
}
