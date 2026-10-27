import {useQuery} from "@tanstack/react-query";
import {queryKeys} from "../../api/queryKeys";
import {getEntityType} from "../../entities/registry";
import {type FilterOption, optionSourceFor} from "../../fields/optionSources";

/** The type field of a table's catalog: how a list tells its types apart. */
const isTypeField = (field: { id: string; valueBinding?: string | null }) => field.id === "type" || field.valueBinding === "type";

export interface TableTypes {
  // The field a view filters on to keep one type; undefined for a table with no type.
  fieldId?: string;
  options: FilterOption[];
}

/**
 * The types of a table, as its own type filter offers them (the concepts of its type field in the organization).
 * A table whose catalog has no type field has none: a card on it just opens the table.
 */
export function useTableTypes(entityType: string | undefined, organizationId: number | undefined) {
  return useQuery<TableTypes>({
    queryKey: queryKeys.tableTypeOptions(organizationId, entityType ?? ""),
    enabled: entityType != null && organizationId != null,
    staleTime: 5 * 60_000,
    queryFn: async () => {
      const catalog = await getEntityType(entityType!)?.list.schema?.load({ organizationId });
      const field = catalog && Object.values(catalog.fields).find(isTypeField);
      const source = field && optionSourceFor(field, organizationId!);
      return field && source ? { fieldId: field.id, options: await source() } : { options: [] };
    },
  });
}
