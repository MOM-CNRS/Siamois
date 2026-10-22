import type { ColumnDef } from "../shared/types";
import type { StudySummary } from "./types";
import { t } from "../../i18n";

// The pinned columns; the rest of the table comes from the project's study forms (the dynamic
// catalog, entities/shared/typeCatalog.ts). Field ids are the study table's system fields (StudyForm).
export const studyColumns: ColumnDef<StudySummary>[] = [
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
    fieldId: "-803",
    header: t("common.type"),
    render: (row) => row.type?.resolvedLabel ?? "",
  },
];
