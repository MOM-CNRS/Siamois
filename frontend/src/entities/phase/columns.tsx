import type { ColumnDef } from "../types";
import type { PhaseSummary } from "./types";

// Pinned, hand-written columns only — no EntityTypeConfig.list.schema for Phase yet (reduced
// scope, migration plan lot 2: dynamic columns via GET /api/v1/projects/{id}/phase-types are
// deferred, same as per-column f.<key> filters — same reduction as Mobilier's own lot 1).
export const phaseColumns: ColumnDef<PhaseSummary>[] = [
  {
    key: "identifier",
    header: "Identifiant",
    sortable: true,
    filterable: true,
    identifier: true,
    render: (row) => row.label || row.identifier || "",
  },
  {
    key: "type",
    fieldId: "-502",
    header: "Type",
    render: (row) => row.type?.resolvedLabel ?? "",
  },
  {
    key: "title",
    fieldId: "-503",
    header: "Titre",
    render: (row) => row.title ?? "",
  },
];
