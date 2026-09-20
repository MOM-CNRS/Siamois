import type { ColumnDef } from "../types";
import type { ProjectSummary } from "./types";

// Scoped to what ProjectResource actually returns over the REST API — NOT the full column set
// ActionUnitTableDefinitionFactory shows in JSF (status, oaCode, openingRate, periods, subjects,
// scientificManager, ...). Those are ActionUnitForm fields with no equivalent on ProjectResource
// today (the API was never asked to expose them for a list view) — bringing them into this list
// would need new fields on ProjectResource, which is real backend work, not a frontend config
// choice, and out of scope for this phase. Sortable only on the columns the API's own
// ALLOWED_PROJECT_SORT_FIELDS accepts (name, identifier, fullIdentifier, creationTime) — see
// ProjectApiService.parseProjectSort.
function formatDate(value?: string | null): string {
  if (!value) return "";
  return value.slice(0, 10);
}

export const projectColumns: ColumnDef<ProjectSummary>[] = [
  {
    key: "fullIdentifier",
    header: "Identifiant",
    sortable: true,
    render: (row) => row.fullIdentifier || row.identifier,
  },
  {
    key: "name",
    header: "Nom",
    sortable: true,
    render: (row) => row.name,
  },
  {
    key: "type",
    header: "Type",
    render: (row) => row.type?.resolvedLabel ?? "",
  },
  {
    key: "beginDate",
    header: "Début",
    render: (row) => formatDate(row.beginDate),
  },
  {
    key: "endDate",
    header: "Fin",
    render: (row) => formatDate(row.endDate),
  },
  {
    key: "mainLocation",
    header: "Localisation",
    render: (row) => row.mainLocation?.name ?? "",
  },
  {
    key: "recordingUnits",
    header: "UE",
    render: (row) => row._counts?.recordingUnits ?? 0,
  },
];
