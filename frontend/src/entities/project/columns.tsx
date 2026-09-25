import type { ColumnDef } from "../types";
import type { ProjectSummary } from "./types";

// Pinned, hand-written columns only — the identifier chip (also the navigation link), the name
// (ActionUnitTableDefinitionFactory's own leading FormFieldColumn, kept hand-written rather than
// catalog-driven because the table's own nameField (id -151) is a locally-built duplicate of the
// fiche catalog's NAME_FIELD (id -102), not the same CustomField instance), and the recording-unit
// relation count. Every other JSF list column (status, oaCode, mainLocation, openingRate, periods,
// subjects, scientificManager, and the ~20 more available via the column toggler) is now dynamic,
// driven by ActionUnitTableColumnDefaults through EntityTypeConfig.list.schema — see config.tsx.
//
// Note this is a parity CORRECTION, not just an addition: the previous 7-column list showed `type`,
// `beginDate` and `endDate`, none of which ActionUnitTableDefinitionFactory ever put in the JSF
// list. Those three are dropped here; `mainLocation` moves from a hand-written column to a dynamic
// one (it's already default-visible in ActionUnitTableColumnDefaults).
export const projectColumns: ColumnDef<ProjectSummary>[] = [
  {
    // JSF's merged statusIdActionsCol: EntityListPanel puts the row's validation state next to the
    // navigation chip, in this one column. `render` returns the
    // chip's LABEL — EntityListPanel wraps it in the chip itself, using the entity's own icon.
    key: "fullIdentifier",
    header: "Identifiant",
    sortable: true,
    filterable: true,
    identifier: true,
    render: (row) => row.fullIdentifier || row.identifier,
  },
  {
    key: "name",
    header: "Nom",
    sortable: true,
    filterable: true,
    render: (row) => row.name,
  },
  {
    // ProjectApiService.ALLOWED_PROJECT_SORT_FIELDS accepts "recordingUnitCount" as a synthetic
    // sort key (ActionUnitSpec.orderByRecordingUnitCount) — using it as the column key makes
    // DataTable's onSort send the right field even though the cell itself renders _counts directly.
    key: "recordingUnitCount",
    header: "UE",
    sortable: true,
    render: (row) => row._counts?.recordingUnits ?? 0,
  },
];
