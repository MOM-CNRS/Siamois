import { useState, type SyntheticEvent } from "react";
import { CellEditOverlay, type CellEditTarget } from "../components/table/CellEditOverlay";
import { renderAnswerCell, renderAnswerValue } from "./display";
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
  organizationId?: number;
  // Registry key of the entity the fiche shows (see CellEditOverlayProps.entityType).
  entityType?: string;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  onSaved: () => void;
}

export function FieldEditCell<TRow extends { id?: string | number }>({
  field,
  row,
  stored,
  readOnly: readOnlyProp,
  required,
  organizationId,
  entityType,
  onSave,
  onSaved,
}: FieldEditCellProps<TRow>) {
  const [editTarget, setEditTarget] = useState<CellEditTarget<TRow> | null>(null);
  // A field with no editor (action code, address) is shown, never offered for editing.
  const readOnly = readOnlyProp || !hasFieldRenderer(field.answerType);

  function open(e: SyntheticEvent) {
    if (readOnly) return;
    const anchor = (e.currentTarget as HTMLElement).getBoundingClientRect();
    setEditTarget({ row, field, anchor, required });
  }

  if (readOnly) {
    const content = renderAnswerCell(field, stored);
    return <span className="field-value-cell field-value-cell-readonly">{content}</span>;
  }

  const content = renderAnswerCell(field, stored);
  return (
    <>
      <span
        className="field-value-cell field-value-cell-editable"
        role="button"
        tabIndex={0}
        // Same affordance as the table's own editable cell: the full value as the native tooltip,
        // falling back to naming the field when it's empty.
        title={renderAnswerValue(field, stored) || `Modifier « ${field.label} »`}
        onClick={open}
      >
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
