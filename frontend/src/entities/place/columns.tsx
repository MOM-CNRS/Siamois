import type { ColumnDef } from "../types";
import type { PlaceSummary } from "./types";
import { t } from "../../i18n";

// The one pinned column: the name, the chip that opens the fiche. Every other column (type, code,
// place number) comes from the field catalog (catalog.ts), sortable and filterable like the other
// lists'. Sorts on the name follow ProjectApiService.ALLOWED_PLACE_SORT_FIELDS.
export const placeColumns: ColumnDef<PlaceSummary>[] = [
  {
    key: "name",
    header: t("common.name"),
    sortable: true,
    identifier: true,
    render: (row) => row.name ?? "",
  },
];
