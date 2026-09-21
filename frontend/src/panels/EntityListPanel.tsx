import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { DataTable, type DataTableStateEvent } from "primereact/datatable";
import { Column } from "primereact/column";
import { InputText } from "primereact/inputtext";
import { Panel } from "primereact/panel";
import { Toolbar } from "primereact/toolbar";
import { Chip } from "primereact/chip";
import { Button } from "primereact/button";
import { getEntityType } from "../entities/registry";
import type { ListParams } from "../entities/types";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import type { PanelToolbarSlot } from "../mountOptions";

export interface EntityListPanelProps {
  entityType: string;
  organizationId?: number;
  onNavigate?: (entityType: string, id: string | number) => void;
  // Bridged from the list's own tableToolbar.xhtml create button (ActionUnitListPanel.
  // configureTableColumns's toolbarCreateConfig — a different gate than the main panel's own
  // creationUnitKind, and rendered inside the table's toolbar, not the panel titlebar). Omitted
  // entirely (no button) when JSF didn't wire one up either.
  onCreate?: () => void;
  // Omitted once the panel has navigated away from the entity this toolbar was built for
  // (App.tsx), or for a render with nothing to bridge to (tests).
  toolbar?: PanelToolbarSlot;
}

const DEFAULT_LIMIT = 20;

/**
 * Generic entity list — entirely driven by the registry (plan §3). No entity-specific branching
 * here: entities/<type>/config.tsx supplies columns/search/sort/pagination via config.list and
 * config.api.list. Project's own columns land in phase 4 (entities/project/config.tsx); this
 * ships the mechanism only, so it's a config-only change to add the next entity's list.
 */
export function EntityListPanel({ entityType, organizationId, onNavigate, onCreate, toolbar }: EntityListPanelProps) {
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
    // panel/header/actionUnitListPanelHeader.xhtml: icon + title + a count chip, plus the
    // generic toolbar — one single PanelHeaderBar passed as this <Panel>'s `header` (plan §7/§8
    // follow-up: "toolbar is part of the header", not PrimeReact's separate `icons` slot),
    // matching the real markup's one sideview-titlebar div rather than a separate strip above
    // this component.
    <Panel
      className="entity-list-panel"
      header={
        <PanelHeaderBar
          title={
            <div className="entity-list-panel-header" style={{ display: "flex", alignItems: "center", gap: "0.5em" }}>
              <i className={config.icon} style={{ fontSize: "2rem", color: "var(--main-color)" }} />
              <span style={{ paddingRight: "0.5em" }}>{config.labels.plural}</span>
              <Chip label={String(data?.totalCount ?? 0)} style={{ background: "transparent", color: "var(--main-color)" }} />
            </div>
          }
          toolbar={toolbar}
        />
      }
    >
      {(config.list.searchable || onCreate) && (
        // p:toolbar (pages/shared/table/tableToolbar.xhtml) → PrimeReact Toolbar, not a plain
        // div — its own generated classes are what render the chrome the stock theme actually
        // paints, the legacy class name alone doesn't. Search on the left, create on the right,
        // same as the real toolbar's two toolbarGroups.
        <Toolbar
          className="entity-list-panel-toolbar"
          start={
            config.list.searchable && (
              <span className="p-input-icon-left">
                <i className="bi bi-search" />
                <InputText
                  placeholder={`Search ${config.labels.plural}`}
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value);
                    setOffset(0);
                  }}
                />
              </span>
            )
          }
          end={
            onCreate && <Button label="Créer" icon="bi bi-plus-square" onClick={onCreate} />
          }
        />
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
    </Panel>
  );
}
