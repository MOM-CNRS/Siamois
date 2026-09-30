import type { ColumnDef } from "../types";
import type { ContainerSummary } from "./types";
import { t } from "../../i18n";

// Pinned, hand-written columns only — no EntityTypeConfig.list.schema for Container yet (reduced
// scope, migration plan lot 3: dynamic columns via GET /api/v1/projects/{id}/container-types are
// deferred, same as per-column f.<key> filters — same reduction set for Phase/Mobilier).
export const containerColumns: ColumnDef<ContainerSummary>[] = [
  {
    key: "identifier",
    header: t("common.identifier"),
    sortable: true,
    filterable: true,
    identifier: true,
    render: (row) => row.identifier ?? "",
  },
  {
    key: "type",
    fieldId: "-602",
    header: t("common.type"),
    render: (row) => row.type?.resolvedLabel ?? "",
  },
];
