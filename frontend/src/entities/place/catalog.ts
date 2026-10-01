import { loadTypeCatalog } from "../typeCatalog";
import type { FieldCatalog, ListScope } from "../types";

// SpatialUnit.DETAILS_FORM's system field ids (the same ones api.ts writes through).
const NAME_FIELD_ID = "-202";
const TYPE_FIELD_ID = "-201";
const PLACE_NUMBER_FIELD_ID = "-205";

// What the list showed before it had a column picker: the type and the place number (the name is
// the pinned identifier column).
const SHOWN_BY_DEFAULT = new Set([TYPE_FIELD_ID, PLACE_NUMBER_FIELD_ID]);

/**
 * The place list's column catalog: GET /api/v1/organizations/{id}/place-types. Places belong to
 * their organization, not to a project, and have no per-type form or additional field — so it is
 * the same catalog in every place list, the tab of a place's contained places included (whose scope
 * is a place, which would otherwise find no catalog at all).
 *
 * The name stays out of the picker: it is the pinned column (the chip that opens the fiche), and
 * offering it again would draw it twice. Its field stays in `fields`, which is what a cell reads.
 */
export async function loadPlaceCatalog(ctx: { organizationId?: number; scope?: ListScope }): Promise<FieldCatalog> {
  const catalog = await loadTypeCatalog({ organizationId: ctx.organizationId }, "place-types");
  return {
    fields: catalog.fields,
    columns: catalog.columns
      .filter((column) => column.fieldId !== NAME_FIELD_ID)
      .map((column, order) => ({ ...column, order, visible: SHOWN_BY_DEFAULT.has(column.fieldId) })),
  };
}
