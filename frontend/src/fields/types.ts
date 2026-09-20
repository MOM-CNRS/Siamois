// Mirrors fr.siamois.ui.api.openapi.v1.resource.form.FieldResource exactly (field-side of the
// "field vs field-configuration" split, plan §6) — this is the shared catalog entry, not a
// per-type config row. Keep in sync with that record if it changes.
export interface FieldResource {
  id: string;
  resourceType: string;
  label: string;
  answerType: string;
  hint?: string | null;
  isSystemField: boolean;
  valueBinding?: string | null;
  fieldCode?: string | null;
}

/**
 * Resolves a field's valueBinding into read/write functions against an entity object. Two kinds,
 * matching how FieldResource.valueBinding already models this (plan §3):
 * - system field (isSystemField=true, Project's case): valueBinding is a fixed property path on
 *   the entity itself (e.g. "name", "type", "beginDate").
 * - non-system field (future RU-in-main-panel case): valueBinding is a key into a dynamic
 *   answer map, not a property on the entity — the entity is expected to expose an `answers`
 *   record for this to work.
 */
export interface ResolvedBinding {
  read(entity: unknown): unknown;
  write<T extends object>(entity: T, value: unknown): T;
}

interface EntityWithAnswers {
  answers?: Record<string, unknown>;
}

export function resolveValueBinding(field: FieldResource): ResolvedBinding {
  const key = field.valueBinding ?? field.id;

  if (field.isSystemField) {
    return {
      read: (entity) => (entity as Record<string, unknown>)[key],
      write: <T extends object>(entity: T, value: unknown): T => ({ ...entity, [key]: value }) as T,
    };
  }

  return {
    read: (entity) => (entity as EntityWithAnswers).answers?.[key],
    write: <T extends object>(entity: T, value: unknown): T => {
      const withAnswers = entity as T & EntityWithAnswers;
      return {
        ...withAnswers,
        answers: { ...withAnswers.answers, [key]: value },
      } as T;
    },
  };
}
