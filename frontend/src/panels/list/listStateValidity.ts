import type {EntityTypeConfig, FieldCatalog} from "../../entities/shared/types";
import {filterKindForField} from "../../fields/optionSources";
import type {FilterValue} from "./tableState";

// What a user kept on a list may name a field or a column the list no longer has: a custom field removed
// since, a column the entity stopped declaring. The server answers 400 « Champ inconnu » to a filter or a
// sort on one, and a filter nobody can see can't be taken off: what no longer fits is dropped before the
// list is first requested. These are the checks, against the same definitions the list is drawn from.

type ListConfig = EntityTypeConfig<unknown, unknown> | undefined;

function sortKeyOf(sort: string | undefined): string | undefined {
  return sort?.split(":")[0] || undefined;
}

/** Whether `key` can still be filtered on: a pinned column declared filterable, or a catalog field with a filter. */
export function isFilterAvailable(key: string, config: ListConfig, catalog: FieldCatalog | undefined): boolean {
  if ((config?.list.columns ?? []).some((c) => c.key === key && c.filterable === true)) return true;
  const field = catalog?.fields[key];
  return field != null && filterKindForField(field) != null;
}

/** The saved filters that can still be applied. */
export function availableFilters(
  filters: Record<string, FilterValue>,
  config: ListConfig,
  catalog: FieldCatalog | undefined,
): Record<string, FilterValue> {
  return Object.fromEntries(Object.entries(filters).filter(([key]) => isFilterAvailable(key, config, catalog)));
}

/** Whether the list can still be ordered by `sort` ("<key>:asc|desc"): the entity's default, a sortable pinned column or a sortable catalog field. */
export function isSortAvailable(sort: string | undefined, config: ListConfig, catalog: FieldCatalog | undefined): boolean {
  const key = sortKeyOf(sort);
  if (key === undefined) return true;
  if (key === sortKeyOf(config?.list.defaultSort)) return true;
  const pinned = (config?.list.columns ?? []).some(
    (c) =>
      (c.fieldId ?? c.key) === key &&
      (c.sortable === true || (c.sortable == null && c.fieldId != null && catalog?.fields[c.fieldId]?.query?.sortable === true)),
  );
  return pinned || catalog?.fields[key]?.query?.sortable === true;
}
