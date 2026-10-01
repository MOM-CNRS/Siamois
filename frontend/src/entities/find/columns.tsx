import type { ColumnDef } from "../types";
import type { FindSummary } from "./types";
import { intlLocale, t } from "../../i18n";

// Pinned, hand-written columns only — no EntityTypeConfig.list.schema for Find yet (reduced
// scope, migration plan lot 1: dynamic columns via GET /api/v1/projects/{id}/find-types are
// deferred, same as per-column f.<key> filters). Mirrors the identifier chip
// SpecimenTableDefinitionFactory's own CommandLink column renders, plus type and collection date.
export const findColumns: ColumnDef<FindSummary>[] = [
  {
    key: "fullIdentifier",
    header: t("common.identifier"),
    sortable: true,
    filterable: true,
    identifier: true,
    render: (row) => row.fullIdentifier ?? "",
  },
  {
    // The find's parent UE — a chip opening that UE's overview, on the organization-wide list and
    // in a project's Mobilier tab alike (the UE differs from row to row in both).
    key: "recordingUnit",
    fieldId: "-401",
    header: t("column.recordingUnitShort"),
    render: (row) => row.recordingUnit?.fullIdentifier ?? "",
    link: (row) => (row.recordingUnit ? { entityType: "recordingUnit", id: row.recordingUnit.id } : null),
  },
  {
    key: "type",
    fieldId: "-409",
    header: t("common.category"),
    render: (row) => row.type?.resolvedLabel ?? "",
  },
  {
    key: "collectionDate",
    header: t("column.collectionDate"),
    sortable: true,
    render: (row) => (row.collectionDate ? new Date(row.collectionDate).toLocaleDateString(intlLocale()) : ""),
  },
];
