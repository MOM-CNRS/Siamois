import type { Condition, FieldRules, RuledColumn } from "./types";

function collect(condition: Condition | undefined | null, out: Set<string>) {
  if (!condition) return;
  if ("all" in condition) condition.all.forEach((c) => collect(c, out));
  else if ("any" in condition) condition.any.forEach((c) => collect(c, out));
  else if ("not" in condition) collect(condition.not, out);
  else out.add(String(condition.fieldId));
}

/** Every field id a set of rules reads. */
export function ruleDependencies(rules: FieldRules | null | undefined): Set<string> {
  const out = new Set<string>();
  if (!rules) return out;
  collect(rules.enabledWhen, out);
  collect(rules.requiredWhen, out);
  if (rules.options) out.add(String(rules.options.fieldId));
  rules.constraints?.forEach((c) => out.add(String(c.fieldId)));
  rules.placeSources?.forEach((source) =>
    Object.values(source.params ?? {}).forEach((binding) => out.add(String(binding.fromField))),
  );
  return out;
}

/**
 * The field ids the given columns' rules read — what a list must request (`fields=`) on top of its
 * visible columns so each row carries the values its cells' rules need. Constraints are symmetric,
 * so a column that is the TARGET of another column's constraint pulls the declaring field in too.
 */
export function columnsDependencies(visible: Iterable<string>, allColumns: Iterable<RuledColumn>): Set<string> {
  const wanted = new Set([...visible].map(String));
  const out = new Set<string>();
  for (const col of allColumns) {
    if (col.fieldId == null) continue;
    const id = String(col.fieldId);
    if (wanted.has(id)) ruleDependencies(col.rules).forEach((d) => out.add(d));
    for (const c of col.rules?.constraints ?? []) {
      if (wanted.has(String(c.fieldId))) out.add(id);
    }
  }
  wanted.forEach((id) => out.delete(id));
  return out;
}
