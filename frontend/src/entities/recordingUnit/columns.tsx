import type { ColumnDef } from "../types";
import type { RecordingUnitSummary } from "./types";

// Pinned, hand-written columns — the structural ones RecordingUnitTableColumnDefaults excludes
// (identifierCol, relationships, specimen): no CustomField behind them, built from `fullIdentifier`
// and `_counts` directly. Every other JSF list column (isPartOf, type, spatial, author, ...) is
// dynamic, driven by RecordingUnitTableColumnDefaults through EntityTypeConfig.list.schema — see
// config.tsx and recordingUnitTypes.ts.
export const recordingUnitColumns: ColumnDef<RecordingUnitSummary>[] = [
  {
    key: "fullIdentifier",
    header: "Identifiant",
    sortable: true,
    filterable: true,
    identifier: true,
    render: (row) => row.fullIdentifier || row.identifier || "",
  },
  {
    // RecordingUnitTableColumnDefaults' own "isPartOf"/"contains" columns are field-catalog-backed
    // (SELECT_MULTIPLE_RECORDING_UNIT) for JSF's chip picker, but RecordingUnitAnswersProjector
    // never projects them on this list (the collections aren't loaded — only the counts are). This
    // pinned column is the one that actually has a value: RecordingUnitSpec.PARENTS_COUNT_SORT is
    // the matching synthetic sort key the backend already accepts.
    key: "parentsCount",
    header: "Parents",
    sortable: true,
    render: (row) => row._counts?.parents ?? 0,
  },
  {
    key: "childrenCount",
    header: "Enfants",
    sortable: true,
    render: (row) => row._counts?.children ?? 0,
  },
  {
    key: "relationshipCount",
    header: "Relations",
    sortable: true,
    render: (row) => row._counts?.relationships ?? 0,
  },
  {
    key: "specimenCount",
    header: "Mobilier",
    sortable: true,
    render: (row) => row._counts?.finds ?? 0,
  },
];
