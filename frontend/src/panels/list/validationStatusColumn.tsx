import type {ColumnDef, EntityTypeConfig} from "../../entities/shared/types";
import type {FilterOption} from "../../fields/optionSources";
import {ValidationStatusCell, type ValidationStatusCellProps} from "../../components/table/ValidationStatusCell";
import {VALIDATION_PRESENTATION, VALIDATION_STATUSES} from "../../components/table/ValidationStatusBadge";
import {t} from "../../i18n";
import type {RowRecord} from "./useEntityListData";

// The reserved id the server sorts and filters the validation status by (sort=-901:asc,
// f.-901=VALIDATED) — ListOnlyColumn.VALIDATION_STATUS on the Java side. Not a form field: the
// status has its own picker on the fiche, so it is no catalog entry either.
export const VALIDATION_STATUS_COLUMN_KEY = "-901";

type ListConfig = EntityTypeConfig<unknown, unknown> | undefined;

// The statuses offered by the column's filter, in workflow order.
const STATUS_OPTIONS: FilterOption[] = VALIDATION_STATUSES.map((status) => ({
  id: status,
  label: VALIDATION_PRESENTATION[status].title,
}));

/**
 * The validation status column every list has (`validationStatus` is a root property of every entity,
 * TraceableEntity): the status picker when the user may change it right now, the read-only badge
 * otherwise. Sorted in workflow order (en cours, terminé, validé, annulé), filtered by status.
 */
export function validationStatusColumn(config: EntityTypeConfig<unknown, unknown>): ColumnDef<RowRecord> {
  return {
    key: VALIDATION_STATUS_COLUMN_KEY,
    header: t("list.statusHeader"),
    sortable: true,
    className: "entity-list-panel-status-cell",
    filterOptions: STATUS_OPTIONS,
    render: (row) => (
      <ValidationStatusCell
        entityType={config.key}
        collectionPath={config.collectionPath}
        row={row as ValidationStatusCellProps["row"]}
      />
    ),
  };
}

/**
 * The pinned columns a list shows: the validation status first, then the entity's own. It comes
 * first in the table (frozen with the identifier) and in the list of filters alike.
 */
export function pinnedColumnsOf(config: ListConfig): ColumnDef<RowRecord>[] {
  if (!config) return [];
  return [validationStatusColumn(config), ...(config.list.columns as ColumnDef<RowRecord>[])];
}
