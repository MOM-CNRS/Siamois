import type { ColumnDef } from "../types";
import type { FindSummary } from "./types";

// Pinned, hand-written columns only — no EntityTypeConfig.list.schema for Find yet (reduced
// scope, migration plan lot 1: dynamic columns via GET /api/v1/projects/{id}/find-types are
// deferred, same as per-column f.<key> filters). Mirrors the identifier chip
// SpecimenTableDefinitionFactory's own CommandLink column renders, plus type and collection date.
export const findColumns: ColumnDef<FindSummary>[] = [
  {
    key: "fullIdentifier",
    header: "Identifiant",
    sortable: true,
    filterable: true,
    identifier: true,
    render: (row) => row.fullIdentifier ?? "",
  },
  {
    key: "type",
    header: "Catégorie",
    render: (row) => row.type?.resolvedLabel ?? "",
  },
  {
    key: "collectionDate",
    header: "Date de collecte",
    sortable: true,
    render: (row) => (row.collectionDate ? new Date(row.collectionDate).toLocaleDateString("fr-FR") : ""),
  },
];
