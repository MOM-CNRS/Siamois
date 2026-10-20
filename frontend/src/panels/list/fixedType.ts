import type { FilterValue } from "../../entities/types";

/**
 * The type a configured tab's fixed filters pin down: an "in" filter on the type field with one value. What is
 * created from that tab is of that type; with several types (or none) nothing is chosen for the user.
 */
export function fixedTypeOf(
  fixedFilters: Record<string, FilterValue> | undefined,
  catalog: { fields: Record<string, { valueBinding?: string | null }> } | undefined,
): string | undefined {
  if (!fixedFilters) return undefined;
  for (const [key, filter] of Object.entries(fixedFilters)) {
    const isType = key === "type" || catalog?.fields[key]?.valueBinding === "type";
    if (isType && filter.op === "in" && filter.v.length === 1) return filter.v[0];
  }
  return undefined;
}
