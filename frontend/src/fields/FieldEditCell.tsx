import { useState, type SyntheticEvent } from "react";
import { CellEditOverlay, type CellEditTarget } from "../components/table/CellEditOverlay";
import type { FieldState } from "../rules";
import { renderAnswerCell, renderAnswerValue } from "./display";
import { describeIncoherence } from "./incoherence";
import { hasFieldRenderer } from "./registry";
import type { AnswerInputBody, FieldResource } from "./types";

/**
 * The fiche's own field value, edited exactly the way a table cell is: a plain read-only display
 * until clicked, then CellEditOverlay itself (unmodified — the same component the list uses, not
 * a fiche-specific reimplementation of its commit/portal/positioning logic) opens on top of it.
 * FieldLabel stays separate and permanently visible above this — a table cell has no per-cell
 * label of its own (the column header covers that), but the fiche does.
 *
 * <p>One CellEditOverlay per field, not one shared instance for the whole fiche the way
 * EntityListPanel shares a single instance across a whole table: that sharing exists there because
 * a table can have hundreds of rows, and a fiche has on the order of thirty fields total, so the
 * simplicity of "the field owns its own open/closed state" outweighs the (here negligible) cost of
 * a few dozen idle overlay instances, each of which renders nothing until clicked.</p>
 */
export interface FieldEditCellProps<TRow extends { id?: string | number }> {
  field: FieldResource;
  row: TRow;
  stored: unknown;
  readOnly: boolean;
  required?: boolean;
  // The field's state under the form's rules (FormLayoutView): a disabled field is shown read-only
  // (greyed) and, when it still holds a value, can only be cleared; an incoherent value is flagged.
  fieldState?: FieldState;
  // Names the other field of a constraint in the incoherence message.
  fieldLabelOf?: (fieldId: string) => string;
  organizationId?: number;
  // Registry key of the entity the fiche shows (see CellEditOverlayProps.entityType).
  entityType?: string;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>, value?: unknown) => Promise<unknown>;
  onSaved: () => void;
}

export function FieldEditCell<TRow extends { id?: string | number }>({
  field,
  row,
  stored,
  readOnly: readOnlyProp,
  required,
  fieldState,
  fieldLabelOf = (id) => id,
  organizationId,
  entityType,
  onSave,
  onSaved,
}: FieldEditCellProps<TRow>) {
  const [editTarget, setEditTarget] = useState<CellEditTarget<TRow> | null>(null);
  // A field with no editor (action code, address) is shown, never offered for editing.
  // Nor is a read-only one (the entity's project, a generated identifier).
  const editable = !readOnlyProp && field.readOnly !== true && hasFieldRenderer(field.answerType);
  const disabled = fieldState?.enabled === false;
  const readOnly = !editable || disabled;
  const incoherences = (fieldState?.incoherent ?? []).map((r) => describeIncoherence(r, fieldLabelOf));

  // Same gesture as a list cell: the click opens the overlay, to edit the field or — read-only —
  // to show it, its chips linking to their fiches.
  function open(e: SyntheticEvent) {
    const anchor = (e.currentTarget as HTMLElement).getBoundingClientRect();
    // Clearing stays possible on a field the user could edit if it weren't for the rules: that is
    // how an incoherent value is resolved (never cleared automatically).
    setEditTarget({ row, field, anchor, required, readOnly, fieldState, incoherences, canClear: editable, fieldLabelOf });
  }

  const content = renderAnswerCell(field, stored, { all: true });
  // An empty field nobody can edit has nothing to open — just the same dash as any empty field.
  if (readOnly && !content) {
    return (
      <span
        className={`field-value-cell field-value-cell-readonly${disabled ? " field-value-cell-disabled" : ""}`}
        title={disabled ? "Ne s'applique pas avec les réponses actuelles" : undefined}
      >
        <span className="field-value-cell-empty">—</span>
      </span>
    );
  }

  return (
    <>
      <span
        className={`field-value-cell ${readOnly ? "field-value-cell-readonly" : "field-value-cell-editable"}${
          disabled ? " field-value-cell-disabled" : ""
        }${incoherences.length > 0 ? " field-value-cell-incoherent" : ""}`}
        role="button"
        tabIndex={0}
        // Same affordance as the table's own editable cell: the full value as the native tooltip,
        // falling back to naming the field when it's empty. An incoherent value says why first.
        title={
          incoherences.length > 0
            ? incoherences.join("\n")
            : renderAnswerValue(field, stored) || `Modifier « ${field.label} »`
        }
        onClick={open}
      >
        {incoherences.length > 0 && (
          <i className="bi bi-exclamation-triangle-fill field-value-cell-warning" aria-label="Valeur incohérente" />
        )}
        {content || <span className="field-value-cell-empty">—</span>}
      </span>
      <CellEditOverlay
        target={editTarget}
        organizationId={organizationId}
        entityType={entityType}
        onSave={onSave}
        onSaved={onSaved}
        onClose={() => setEditTarget(null)}
      />
    </>
  );
}
