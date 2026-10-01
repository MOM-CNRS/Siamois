import type { ColumnDef } from "../types";
import type { RecordingUnitSummary } from "./types";
import { t } from "../../i18n";

// The one pinned, hand-written column — the identifier chip RecordingUnitTableColumnDefaults
// excludes (identifierCol): no CustomField behind it, built from `fullIdentifier` directly. Every
// other column (parents, children, stratigraphic relationships, finds, type, spatial, author, ...)
// is dynamic, driven by RecordingUnitTableColumnDefaults through EntityTypeConfig.list.schema — see
// config.tsx and recordingUnitTypes.ts. The relation columns carry a preview and a total per row
// (MultiValue), not their whole list.
export const recordingUnitColumns: ColumnDef<RecordingUnitSummary>[] = [
  {
    key: "fullIdentifier",
    header: t("common.identifier"),
    sortable: true,
    filterable: true,
    identifier: true,
    render: (row) => row.fullIdentifier || row.identifier || "",
  },
];
