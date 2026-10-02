// The registry keys of the application's entity types. Maps keyed by entity type (colours,
// reference targets…) are typed with EntityKey so a missing or misspelled key fails to compile
// instead of silently yielding nothing. The registry itself stays keyed by string: it also
// resolves keys read from URLs and the JSF bridge, and tests register fake types.
export const ENTITY_KEYS = ["project", "recordingUnit", "find", "phase", "container", "place", "document"] as const;

export type EntityKey = (typeof ENTITY_KEYS)[number];

// The entity every project-scoped entity lives in: a scope or a row of this type IS the project.
export const PROJECT_KEY: EntityKey = "project";

export function isEntityKey(value: string | undefined): value is EntityKey {
  return value != null && (ENTITY_KEYS as readonly string[]).includes(value);
}
