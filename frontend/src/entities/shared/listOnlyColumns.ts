// The reserved ids the server filters and sorts the list-only columns by (ListOnlyColumn on the Java
// side): no form field, yet queried through the same f.<fieldId> / sort=<fieldId> contract as one, with
// every operator of their kind.

/**
 * The validation status every entity carries (sort=-901:asc, f.-901=VALIDATED). Not a form field: the status has
 * its own picker on the fiche, so it is no catalog entry either.
 */
export const VALIDATION_STATUS_COLUMN_KEY = "-901";

/** The identifier a list opens the fiche by (the full one for recording units, finds and projects). */
export const IDENTIFIER_COLUMN_KEY = "-902";

/** The name, on the entities that have one (a project). */
export const NAME_COLUMN_KEY = "-903";

/**
 * How an identifier column filters (spread into its ColumnDef): as a text, every text operator included,
 * through the reserved id rather than the column's own key.
 */
export const IDENTIFIER_COLUMN_FILTER = { filterable: true, filterKey: IDENTIFIER_COLUMN_KEY } as const;

/** How a name column filters: like an identifier column ({@link IDENTIFIER_COLUMN_FILTER}). */
export const NAME_COLUMN_FILTER = { filterable: true, filterKey: NAME_COLUMN_KEY } as const;
