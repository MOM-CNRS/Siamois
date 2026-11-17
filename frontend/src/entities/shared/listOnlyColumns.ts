// The reserved ids the server filters and sorts the list-only columns by (ListOnlyColumn on the Java
// side): no form field, yet queried through the same f.<fieldId> / sort=<fieldId> contract as one, with
// every operator of their kind.

/** The validation status every entity carries. */
export const VALIDATION_STATUS_COLUMN_KEY = "-901";

/** The identifier a list opens the fiche by (the full one for recording units, finds and projects). */
export const IDENTIFIER_COLUMN_KEY = "-902";

/** The name, on the entities that have one (a project). */
export const NAME_COLUMN_KEY = "-903";
