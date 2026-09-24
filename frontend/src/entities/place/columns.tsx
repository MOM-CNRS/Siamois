import type { ColumnDef } from "../types";
import type { PlaceSummary } from "./types";

// SpatialUnitTableDefinitionFactory's core columns, reduced like the other organization-wide
// lists: the name (the chip that opens the fiche), its type and its code. Sort keys follow
// ProjectApiService.ALLOWED_PLACE_SORT_FIELDS (name, code, …).
export const placeColumns: ColumnDef<PlaceSummary>[] = [
  {
    key: "name",
    header: "Nom",
    sortable: true,
    identifier: true,
    render: (row) => row.name ?? "",
  },
  {
    key: "type",
    header: "Type",
    render: (row) => row.type?.resolvedLabel ?? "",
  },
  {
    key: "placeNumber",
    header: "N° de regroupement",
    render: (row) => (row.placeNumber != null ? String(row.placeNumber) : ""),
  },
];
