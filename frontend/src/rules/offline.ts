// Offline counterpart of the server-side options filters, for the mobile app: filters an already
// downloaded option list with the relations shipped in the vocabulary bundle (relatedByConcept).

import type { IsAllowed } from "./evaluate";
import type { OptionsContext } from "./types";
import { scalarsOf } from "./values";

export type RelatedByConcept = Record<string, string[]>;

export function filterOptionsOffline<T extends { id: string | number }>(
  options: T[],
  context: OptionsContext | undefined,
  relatedByConcept: RelatedByConcept,
): T[] {
  if (!context || context.kind !== "RELATED_CONCEPTS") return options;
  if (context.relatedTo == null) return [];
  const allowed = new Set(relatedByConcept[context.relatedTo] ?? []);
  return options.filter((o) => allowed.has(String(o.id)));
}

/** An IsAllowed for evaluateForm backed by the offline relations (REF_MATCH stays unknown). */
export function offlineIsAllowed(relatedByConcept: RelatedByConcept): IsAllowed {
  return (_fieldId, value, context) => {
    if (context.kind !== "RELATED_CONCEPTS") return undefined;
    if (context.relatedTo == null) return false;
    const allowed = new Set(relatedByConcept[context.relatedTo] ?? []);
    return scalarsOf(value).every((v) => allowed.has(String(v)));
  };
}
