import type {FieldResource} from "../../../fields/types";
import {type FieldState, type OptionsContext, type PlaceContext, placeContextOf} from "../../../rules";

/**
 * What a bulk editor's picker searches, as the fiche's own editor of the field would: the place
 * sources (INSEE… besides the base's places) and the options the rules narrow the field to.
 */
export interface BulkFieldState {
  placeContext?: PlaceContext;
  optionsContext?: OptionsContext;
}

/**
 * The value every one of them shares, or null when they differ (or there is none).
 */
function shared<T>(values: readonly T[]): T | null {
  const distinct = new Set(values.map((value) => JSON.stringify(value ?? null)));
  return distinct.size === 1 && values[0] != null ? values[0] : null;
}

/**
 * The place context of the selection: the sources apply whatever the rows; a place a source reads
 * from another field is given only when every row has the same one there.
 */
function sharedPlaceContext(field: FieldResource, states: readonly FieldState[]): PlaceContext | undefined {
  const contexts = states.map((state) => state.placeContext).filter((context): context is PlaceContext => context != null);
  if (contexts.length === 0) return placeContextOf(field.rules?.placeSources, () => undefined);
  const deps: Record<string, string | null> = {};
  for (const fieldId of new Set(contexts.flatMap((context) => Object.keys(context.deps)))) {
    deps[fieldId] = contexts.length === states.length ? shared(contexts.map((context) => context.deps[fieldId])) : null;
  }
  return { deps };
}

/**
 * Whether the options depend on a value the selection doesn't share: a picker given such a context
 * would wait for a parent that no single row stands for.
 */
function waitsForParent(context: OptionsContext): boolean {
  if (context.kind === "RELATED_CONCEPTS") return context.relatedTo == null;
  if (context.kind === "REF_MATCH") return context.value == null;
  return false;
}

/**
 * The options context of the selection: the rows' own when they all have the same one, the type's
 * when no row says; none when they differ — the picker then offers the field's whole list, and the
 * server still checks each row.
 */
function sharedOptionsContext(
  field: FieldResource,
  states: readonly FieldState[],
  typeConceptId: string | undefined,
): OptionsContext | undefined {
  if (states.length === 0) {
    return field.rules?.options?.kind === "RELATED_TO_TYPE"
      ? { kind: "RELATED_TO_TYPE", relatedTo: typeConceptId ?? null }
      : undefined;
  }
  const context = shared(states.map((state) => state.optionsContext));
  return context && !waitsForParent(context) ? context : undefined;
}

/**
 * What the bulk editor of `field` hands its renderer so it is the same component, searching the same
 * sources, as on a fiche: derived from the states the list's rules give the field on the selected
 * rows (those the list has them for), else from the field's own rules.
 */
export function bulkFieldState(
  field: FieldResource,
  rowStates: readonly (FieldState | undefined)[],
  typeConceptId: string | undefined,
): BulkFieldState {
  const states = rowStates.filter((state): state is FieldState => state != null);
  return {
    placeContext: sharedPlaceContext(field, states),
    optionsContext: sharedOptionsContext(field, states, typeConceptId),
  };
}
