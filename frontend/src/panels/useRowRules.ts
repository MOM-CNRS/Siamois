import { useMemo } from "react";
import { valueOfField } from "../fields/FormLayoutView";
import type { FieldResource } from "../fields/types";
import { columnsDependencies, evaluateForm, type FieldState, type RuledColumn } from "../rules";

/**
 * The conditional rules of a list's cells. A list has no layout to read them from, so they come off
 * the field catalog (FieldResource.rules — the rules of the field's column in its entity's details
 * form) and are evaluated per row against the row's own values:
 *
 * <ul>
 *   <li>`extraFields` — what the list must request (`fields=`) on top of its visible columns so each
 *   row carries the values those cells' rules read (the nature an erosion cell depends on, the
 *   opening date a closing date is bounded by) even when that column isn't on screen.</li>
 *   <li>`stateOf(row, fieldId)` — the cell's state: disabled, bounded, flagged incoherent.
 *   Undefined for a list with no rules at all, which pays nothing for any of this.</li>
 * </ul>
 *
 * A row is evaluated with the rules the catalog holds for the field, whatever the row's type: the
 * details form is the same layout for every type today (only additional fields and which fields are
 * active/mandatory vary per type, and none of them carries rules), and an organization-wide catalog
 * has no per-type forms to pick from anyway.
 */
/**
 * The field ids a list must request on top of its shown columns so each row carries what the cells'
 * rules read. Empty (and free) for a catalog with no rules.
 */
export function useRuleFields(fields: Record<string, FieldResource> | undefined, shownFieldIds: readonly string[]): string[] {
  const hasRules = useMemo(() => Object.values(fields ?? {}).some(hasRulesOf), [fields]);
  const shownKey = shownFieldIds.join(",");
  return useMemo(() => {
    if (!fields || !hasRules) return NONE;
    const all: RuledColumn[] = Object.values(fields).map((f) => ({ fieldId: f.id, rules: f.rules }));
    const deps = [...columnsDependencies(shownFieldIds, all)].sort();
    return deps.length > 0 ? deps : NONE;
    // shownFieldIds is a fresh array each render; its content is what matters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fields, hasRules, shownKey]);
}

const NONE: string[] = [];

/** Each row's cell states under the catalog's rules; `stateOf` is undefined-valued where there are none. */
export function useRowRules<TRow extends { id?: string | number }>(
  fields: Record<string, FieldResource> | undefined,
  shownFieldIds: readonly string[],
  ruleFields: readonly string[],
  rows: readonly TRow[],
) {
  const shownKey = shownFieldIds.join(",");
  const statesByRow = useMemo(() => {
    const out = new Map<string | number, Map<string, FieldState>>();
    if (!fields || ruleFields.length === 0 && !Object.values(fields).some(hasRulesOf)) return out;
    const evaluated = new Set([...shownFieldIds, ...ruleFields]);
    const columns: RuledColumn[] = [...evaluated]
      .filter((id) => fields[id])
      .map((id) => ({ fieldId: id, rules: fields[id].rules }));
    for (const row of rows) {
      if (row.id == null) continue;
      out.set(row.id, evaluateForm(columns, (id) => valueOfField(row, fields, id)));
    }
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fields, shownKey, ruleFields, rows]);

  return {
    stateOf: (row: TRow, fieldId: string): FieldState | undefined =>
      row.id != null ? statesByRow.get(row.id)?.get(fieldId) : undefined,
    labelOf: (fieldId: string) => fields?.[fieldId]?.label ?? fieldId,
  };
}

function hasRulesOf(field: FieldResource): boolean {
  const r = field.rules;
  return r != null && (r.enabledWhen != null || r.requiredWhen != null || r.options != null || (r.constraints?.length ?? 0) > 0);
}
