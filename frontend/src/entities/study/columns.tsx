import type {ColumnDef} from "../shared/types";
import {IDENTIFIER_COLUMN_KEY} from "../shared/listOnlyColumns";
import type {StudySummary} from "./types";
import {t} from "../../i18n";

// The pinned columns; the rest of the table comes from the project's study forms (the dynamic
// catalog, entities/shared/typeCatalog.ts). Field ids are the study table's system fields (StudyForm).
export const studyColumns: ColumnDef<StudySummary>[] = [
  {
    key: "identifier",
    header: t("common.identifier"),
    sortable: true,
    filterable: true,
    // Filtered as a text, every text operator included, through the reserved id.
    filterKey: IDENTIFIER_COLUMN_KEY,
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
