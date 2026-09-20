import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { DataTable, type DataTableStateEvent } from "primereact/datatable";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import { getEntityType } from "../entities/registry";
import type { ListParams } from "../entities/types";

export interface EntityListPanelProps {
  entityType: string;
  organizationId?: number;
  onNavigate?: (entityType: string, id: string | number) => void;
}

const DEFAULT_LIMIT = 20;

/**
 * Generic entity list — entirely driven by the registry (plan §3). No entity-specific branching
 * here: entities/<type>/config.tsx supplies columns/search/sort/pagination via config.list and
 * config.api.list. Project's own columns land in phase 4 (entities/project/config.tsx); this
 * ships the mechanism only, so it's a config-only change to add the next entity's list.
 */
export function EntityListPanel({ entityType, organizationId, onNavigate }: EntityListPanelProps) {
  const config = getEntityType(entityType);
  const [offset, setOffset] = useState(0);
  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<string | undefined>(config?.list.defaultSort);

  const params: ListParams = { offset, limit, search: search || undefined, sort, organizationId };

  const { data, isLoading, error } = useQuery({
    queryKey: ["entity-list", entityType, params],
    queryFn: () => config!.api.list(params),
    enabled: config != null,
  });

  if (!config) {
    return <div className="entity-list-panel-unsupported">Unknown entity type &quot;{entityType}&quot;</div>;
  }

  function onPage(e: DataTableStateEvent) {
    setOffset(e.first ?? 0);
    setLimit(e.rows ?? DEFAULT_LIMIT);
  }

  function onSortChange(e: DataTableStateEvent) {
    if (!e.sortField) {
      setSort(undefined);
      return;
    }
    setSort(`${e.sortField}:${e.sortOrder === 1 ? "asc" : "desc"}`);
  }

  function onRowClick(e: { data: unknown }) {
    const row = e.data as { id?: string | number };
    if (row.id != null) onNavigate?.(entityType, row.id);
  }

  return (
    <div className="entity-list-panel">
      {config.list.searchable && (
        <div className="entity-list-panel-toolbar">
          <InputText
            placeholder={`Search ${config.labels.plural}`}
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setOffset(0);
            }}
          />
        </div>
      )}
      {error && <div className="entity-list-panel-error">{(error as Error).message}</div>}
      <DataTable
        value={(data?.data ?? []) as Record<string, unknown>[]}
        lazy
        paginator
        first={offset}
        rows={limit}
        totalRecords={data?.totalCount ?? 0}
        loading={isLoading}
        onPage={onPage}
        onSort={onSortChange}
        sortMode="single"
        onRowClick={onRowClick}
      >
        {config.list.columns.map((col) => (
          <Column key={col.key} field={col.key} header={col.header} sortable={col.sortable} body={col.render} />
        ))}
      </DataTable>
    </div>
  );
}
