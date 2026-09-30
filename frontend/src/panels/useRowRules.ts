import { useMemo } from "react";
import { valueOfField } from "../fields/FormLayoutView";
import type { FieldResource } from "../fields/types";
import { columnsDependencies, evaluateForm, type FieldState, type RuledColumn } from "../rules";
import type { RowTypeRef, TypeRules } from "./useTypeRules";

/**
 * The conditional rules of a list's cells, evaluated per row against the row's own values.
 *
 * <p>Rules belong to a type's configuration in a project, so a list of an entity whose form is
 * configurable (`typeRules`, see useTypeRules) evaluates each row with the rules of its own
 * project's form for its own type. A list of an entity whose form is fixed (projects, places) keeps
 * reading them off the field catalog (FieldResource.rules).</p>
 *
 * <ul>
 *   <li>`useRuleFields` — what the list must request (`fields=`) on top of its visible columns so
 *   each row carries the values those cells' rules read (the nature an erosion cell depends on, the
 *   opening date a closing date is bounded by) even when that column isn't on screen.</li>
 *   <li>`stateOf(row, fieldId)` — the cell's state: disabled, bounded, flagged incoherent.
 *   Undefined for a list with no rules at all, which pays nothing for any of this.</li>
 * </ul>
 */

/**
 * The field ids a list must request on top of its shown columns so each row carries what the cells'
 * rules read. Empty (and free) when there are no rules.
 */
export function useRuleFields(
  fields: Record<string, FieldResource> | undefined,
  shownFieldIds: readonly string[],
  typeRules?: TypeRules,
): string[] {
  const hasRules = useMemo(() => Object.values(fields ?? {}).some(hasRulesOf), [fields]);
  const shownKey = shownFieldIds.join(",");
  return useMemo(() => {
    const deps = new Set<string>();
    if (fields && hasRules) {
      const all: RuledColumn[] = Object.values(fields).map((f) => ({ fieldId: f.id, rules: f.rules }));
      columnsDependencies(shownFieldIds, all).forEach((d) => deps.add(d));
    }
    typeRules?.columnSets.forEach((set) => columnsDependencies(shownFieldIds, set).forEach((d) => deps.add(d)));
    return deps.size > 0 ? [...deps].sort() : NONE;
    // shownFieldIds is a fresh array each render; its content is what matters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fields, hasRules, shownKey, typeRules]);
}

const NONE: string[] = [];

/** Each row's cell states under the rules that apply to it; `stateOf` is undefined where there are none. */
export function useRowRules<TRow extends RowTypeRef & { id?: string | number }>(
  fields: Record<string, FieldResource> | undefined,
  shownFieldIds: readonly string[],
  ruleFields: readonly string[],
  rows: readonly TRow[],
  typeRules?: TypeRules,
) {
  const shownKey = shownFieldIds.join(",");
  const statesByRow = useMemo(() => {
    const out = new Map<string | number, Map<string, FieldState>>();
    const catalogRules = fields != null && Object.values(fields).some(hasRulesOf);
    if (!fields || (ruleFields.length === 0 && !catalogRules && (typeRules?.columnSets.length ?? 0) === 0)) return out;
    const evaluated = [...new Set([...shownFieldIds, ...ruleFields])].filter((id) => fields[id]);
    for (const row of rows) {
      if (row.id == null) continue;
      const columns: RuledColumn[] = evaluated.map((id) => ({
        fieldId: id,
        rules: typeRules != null ? (typeRules.rulesOf(row, id) ?? fields[id].rules) : fields[id].rules,
      }));
      out.set(row.id, evaluateForm(columns, (id) => valueOfField(row, fields, id)));
    }
    return out;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fields, shownKey, ruleFields, rows, typeRules]);

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
