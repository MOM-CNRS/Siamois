import { useMemo } from "react";
import { scopeProjectId } from "../../entities/scope";
import type { ColumnDef, EntityTypeConfig, FieldCatalog, ListScope } from "../../entities/types";
import type { ColumnTogglerOption } from "../../components/table/ColumnToggler";
import type { FilterSpec } from "../../components/table/FilterChipBar";
import { renderAnswerCell } from "../../fields/display";
import { filterKindForField, optionSourceFor, type FilterKind } from "../../fields/optionSources";
import { resolveValueBinding, type FieldResource } from "../../fields/types";
import type { RowRecord } from "./useEntityListData";


export interface UseListColumnsOptions {
  config: EntityTypeConfig<unknown, unknown> | undefined;
  catalog: FieldCatalog | undefined;
  visibleColumns: string[];
  pinnedFieldIds: Set<string>;
  organizationId?: number;
  scope?: ListScope;
}

/**
 * The table's columns and what goes with them: the pinned ones the entity declares plus the catalog
 * fields the user shows (in their order), the field behind each of the latter, what the toggler
 * offers, and the filters the visible columns can take.
 */
export function useListColumns({ config, catalog, visibleColumns, pinnedFieldIds, organizationId, scope }: UseListColumnsOptions) {
  const dynamicColumns = useMemo<ColumnDef<RowRecord>[]>(() => {
    if (!catalog) return [];
    const byFieldId = new Map(catalog.columns.map((c) => [c.fieldId, c]));
    // In the user's order (the chooser's "visible" list, or a header dragged in the table); the
    // catalog's own `order` only seeds the defaults.
    return visibleColumns
      .map((fieldId) => byFieldId.get(fieldId))
      .filter((c): c is NonNullable<typeof c> => c != null)
      .map((c): ColumnDef<RowRecord> | null => {
        const field = catalog.fields[c.fieldId];
        if (!field) return null;
        const binding = resolveValueBinding(field);
        return {
          key: field.id,
          header: field.label,
          // The server says which columns it can order (FieldResource.query): sort=<fieldId>:asc,
          // a multi-valued column by its number of values.
          sortable: field.query?.sortable === true,
          // Renders the VALUE only. Whether that value is also a click target is the table's
          // business (EntityTable's cell), not the column's: the editable wrapper has to be the
          // cell's own value slot — it eats the cell's padding to make the whole cell clickable —
          // so it cannot be nested inside one.
          render: (row: RowRecord) => renderAnswerCell(field, binding.readRaw(row)),
        };
      })
      .filter((c): c is ColumnDef<RowRecord> => c != null);
  }, [catalog, visibleColumns]);

  // Each catalog-driven column's field, keyed by the ColumnDef key the cell renderer receives: a
  // click on its cell opens the overlay — to edit it, or to read it. A pinned column has no
  // FieldResource and stays a plain cell (or its own link).
  const fieldByColumn = useMemo<Map<string, FieldResource>>(() => {
    if (!catalog) return new Map();
    const byKey = new Map<string, FieldResource>();
    for (const fieldId of visibleColumns) {
      const field = catalog.fields[fieldId];
      if (field) byKey.set(field.id, field);
    }
    return byKey;
  }, [catalog, visibleColumns]);

  const togglerOptions = useMemo<ColumnTogglerOption[]>(() => {
    if (!catalog) return [];
    return catalog.columns
      .filter((c) => !pinnedFieldIds.has(c.fieldId))
      .slice()
      .sort((a, b) => a.order - b.order)
      .map((c) => ({ fieldId: c.fieldId, label: catalog.fields[c.fieldId]?.label ?? c.fieldId }));
  }, [catalog, pinnedFieldIds]);

  // Filterable columns: pinned ones marked `filterable` (their own key doubles as the f.<key>
  // query param — see ColumnDef.filterable) plus visible catalog columns whose answerType maps to
  // a FilterKind. A column not currently visible gets no filter widget, same as it gets no cell:
  // requesting f.<key> for a column ProjectListFilter doesn't know about is a 400, and a column
  // that isn't shown has nothing for the user to correlate the filter with anyway.
  const filterSpecs = useMemo<FilterSpec[]>(() => {
    const pinned = (config?.list.columns ?? [])
      .filter((c) => c.filterable === true)
      .map((c): FilterSpec => ({ key: c.key, label: c.header, kind: "contains" as FilterKind }));

    if (!catalog) return pinned;
    const byFieldId = new Map(catalog.columns.map((c) => [c.fieldId, c]));
    const dynamic = visibleColumns
      .map((fieldId) => byFieldId.get(fieldId))
      .filter((c): c is NonNullable<typeof c> => c != null)
      .flatMap((c) => {
        const field = catalog.fields[c.fieldId];
        if (!field) return [];
        // f.<fieldId>, whatever the field (system or additional): FieldQueryService resolves it.
        const kind = filterKindForField(field);
        if (!kind) return [];
        const loadOptions =
          organizationId != null ? (optionSourceFor(field, organizationId, scopeProjectId(scope)) ?? undefined) : undefined;
        return [{ key: field.id, label: field.label, kind, loadOptions } satisfies FilterSpec];
      });
    return [...pinned, ...dynamic];
  }, [config, catalog, visibleColumns, organizationId, scope]);

  // A column that only makes sense across several parents (the row's project, on an
  // organization-wide list) is dropped inside a scoped relation tab, where it would repeat the
  // parent on every row.
  const columns = useMemo<ColumnDef<RowRecord>[]>(() => {
    const pinned = ((config?.list.columns ?? []) as ColumnDef<RowRecord>[])
      .filter((c) => !(scope && c.unscopedOnly))
      // A pinned column showing a catalog field sorts by that field's id, if the catalog says it can.
      .map((c) =>
        c.fieldId && c.sortable == null && catalog?.fields[c.fieldId]?.query?.sortable ? { ...c, sortable: true } : c,
      );
    return [...pinned, ...dynamicColumns];
  }, [config, catalog, scope, dynamicColumns]);

  return { columns, fieldByColumn, togglerOptions, filterSpecs };
}
