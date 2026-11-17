import type {ColumnDef} from "../shared/types";
import {IDENTIFIER_COLUMN_KEY} from "../shared/listOnlyColumns";
import type {PhaseSummary} from "./types";
import {t} from "../../i18n";

// Pinned, hand-written columns only — no EntityTypeConfig.list.schema for Phase yet (reduced
// scope, migration plan lot 2: dynamic columns via GET /api/v1/projects/{id}/phase-types are
// deferred, same as per-column f.<key> filters — same reduction as Mobilier's own lot 1).
export const phaseColumns: ColumnDef<PhaseSummary>[] = [
  {
    key: "identifier",
    header: t("common.identifier"),
    sortable: true,
    filterable: true,
    // Filtered as a text, every text operator included, through the reserved id.
    filterKey: IDENTIFIER_COLUMN_KEY,
    identifier: true,
    render: (row) => row.label || row.identifier || "",
  },
  {
    key: "type",
    fieldId: "-502",
    header: t("common.type"),
    render: (row) => row.type?.resolvedLabel ?? "",
  },
  {
    key: "title",
    fieldId: "-503",
    header: t("common.title"),
    render: (row) => row.title ?? "",
  },
];
