import type { ColumnDef } from "../types";
import type { DocumentSummary } from "./types";
import { t } from "../../i18n";

// The pinned columns; the rest of the table comes from the project's document forms (the dynamic
// catalog, entities/typeCatalog.ts). Field ids are the document table's system fields (DocumentForm).
export const documentColumns: ColumnDef<DocumentSummary>[] = [
  {
    key: "identifier",
    header: t("common.identifier"),
    sortable: true,
    filterable: true,
    identifier: true,
    render: (row) => row.identifier || row.label || "",
  },
  {
    key: "title",
    fieldId: "-708",
    header: t("common.title"),
    sortable: true,
    render: (row) => row.title ?? "",
  },
  {
    key: "category",
    fieldId: "-704",
    header: t("entity.document.category"),
    render: (row) => row.type?.resolvedLabel ?? "",
  },
];
