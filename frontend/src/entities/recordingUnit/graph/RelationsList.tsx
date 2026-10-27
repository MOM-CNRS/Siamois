import {type ReactNode, useEffect, useRef} from "react";
import {Button} from "primereact/button";
import {Chip} from "primereact/chip";
import {Column} from "primereact/column";
import {DataTable, type DataTableStateEvent} from "primereact/datatable";
import {Menu} from "primereact/menu";
import {Skeleton} from "primereact/skeleton";
import {FilterChipBar, type FilterSpec} from "../../../components/table/FilterChipBar";
import {refChipStyle} from "../../../fields/display";
import type {FilterOption} from "../../../fields/optionSources";
import {t} from "../../../i18n";
import {ListSearchBox} from "../../../panels/list/ListSearchBox";
import type {FilterValue} from "../../../panels/list/tableState";
import {kindLabel, RELATION_KINDS} from "./labels";
import {KIND_FILTER, type RelationListModel} from "./useRelationList";
import type {RelationLinkEditor} from "./useRelationLinkEditor";
import {isStratigraphy} from "./useRelationLinkEditor";
import type {RelationRow} from "./types";

const SKELETON_ROW_COUNT = 8;
const skeletonRow = (i: number) => ({ key: `__skeleton-${i}`, skeleton: true as const });
const SKELETON_ROWS = Array.from({ length: SKELETON_ROW_COUNT }, (_, i) => skeletonRow(i));
type ViewRow = (RelationRow & { key: string; skeleton?: undefined }) | ReturnType<typeof skeletonRow>;

// The height of every row (--entity-list-row-height in main-panel.css), which the virtual scroller places them by.
const ROW_HEIGHT_PX = 38;

const kindOptions = (): FilterOption[] => RELATION_KINDS.map((kind) => ({ id: kind, label: kindLabel(kind) }));
const kindSpecs = (): FilterSpec[] => [{
  key: KIND_FILTER,
  label: t("recordingUnit.relations.column.kind"),
  kind: "in",
  loadOptions: async (q) => kindOptions().filter((o) => !q || o.label.toLowerCase().includes(q.toLowerCase())),
}];

// The search box and the filter on the kind of relation: what follows the gear in the toolbar.
export function RelationsListControls({ list }: { list: RelationListModel }) {
  const chosen = list.filters[KIND_FILTER];
  const options = chosen?.op === "in" ? kindOptions().filter((o) => chosen.v.includes(o.id)) : [];
  const onChange = (key: string, value: FilterValue | undefined) => {
    const next = { ...list.filters };
    if (value) next[key] = value;
    else delete next[key];
    list.setFilters(next);
  };
  return (
    <>
      <ListSearchBox value={list.search} onSearch={list.setSearch} />
      <FilterChipBar specs={kindSpecs()} filters={list.filters} optionsByKey={{ [KIND_FILTER]: options }} onChange={onChange} />
    </>
  );
}

// What sits at the right of the toolbar: how many relations match, and the means to add one.
export function RelationsListEnd({ list, editor }: { list: RelationListModel; editor?: Pick<RelationLinkEditor, "add"> }) {
  const menu = useRef<Menu>(null);
  const anchor = useRef<HTMLElement | null>(null);
  const addable = RELATION_KINDS.map((kind) => ({
    label: kindLabel(kind),
    command: () => anchor.current && editor?.add(kind, anchor.current),
  }));
  return (
    <>
      {list.isLoading ? (
        <Skeleton width="3rem" height="1.5rem" borderRadius="16px" />
      ) : (
        <Chip className="entity-list-panel-selection-count" label={String(list.total)} />
      )}
      {editor && (
        <>
          <Button
            label={t("recordingUnit.relations.add")}
            icon="bi bi-plus-square"
            aria-haspopup
            onClick={(e) => { anchor.current = e.currentTarget; menu.current?.toggle(e); }}
          />
          <Menu ref={menu} popup model={addable} />
        </>
      )}
    </>
  );
}

export interface RelationsListProps {
  list: RelationListModel;
  // Opens the unit of a relation.
  onOpen: (unitId: string, label: string) => void;
  // Absent when the caller cannot write: the rows then have no actions.
  editor?: Pick<RelationLinkEditor, "edit" | "remove">;
}

const sortOf = (sort: string) => {
  const at = sort.lastIndexOf(":");
  return { field: sort.slice(0, at), order: sort.slice(at + 1) === "desc" ? -1 as const : 1 as const };
};

// The unit's relations as a table, the design of every list of the app: scrolled virtually through the
// whole result set (fetched page by page as the scroll reaches it), sortable by kind or by the other
// unit's identifier, one relation per row.
export function RelationsList({ list, onOpen, editor }: RelationsListProps) {
  const rows: ViewRow[] = list.isLoading
    ? SKELETON_ROWS
    : list.rows.map((row, i) => (row ? { ...row, key: `${row.kind}:${row.unit.resourceId}` } : skeletonRow(i)));
  const { field, order } = sortOf(list.sort);

  // A new search, filter or sort starts at the top: the scroll position belonged to the rows of the previous one.
  const rootRef = useRef<HTMLDivElement>(null);
  const resultSetKey = JSON.stringify([list.search, list.filters, list.sort]);
  useEffect(() => {
    const scroller = rootRef.current?.querySelector(".p-virtualscroller");
    if (scroller) scroller.scrollTop = 0;
  }, [resultSetKey]);

  const onSort = (e: DataTableStateEvent) => list.setSort(e.sortField ? `${e.sortField}:${e.sortOrder === 1 ? "asc" : "desc"}` : undefined);

  const cell = (row: ViewRow, render: (row: RelationRow) => ReactNode, width = "70%") =>
    row.skeleton ? <Skeleton height="1rem" width={width} /> : <span className="entity-list-panel-cell">{render(row)}</span>;

  return (
    <div ref={rootRef} className="sia-contents">
      <DataTable
        value={rows}
        size="small"
        lazy
        virtualScrollerOptions={{
          lazy: true,
          itemSize: ROW_HEIGHT_PX,
          onLazyLoad: (e) => list.setScrollWindow(Number(e.first), Number(e.last)),
        }}
        onSort={onSort}
        sortField={field}
        sortOrder={order}
        sortMode="single"
        dataKey="key"
        cellMemo={false}
        emptyMessage={t("recordingUnit.graph.editor.empty")}
        rowClassName={(row: ViewRow) => (row.skeleton ? "entity-list-panel-skeleton-row" : list.search ? "search-match" : "")}
        scrollable
        scrollHeight="flex"
      >
        <Column
          field="kind"
          sortable
          header={<span className="entity-list-panel-column-header">{t("recordingUnit.relations.column.kind")}</span>}
          body={(row: ViewRow) => cell(row, (r) => <span className="ref-chip" style={refChipStyle("concepts")}>{kindLabel(r.kind)}</span>, "5rem")}
        />
        <Column
          field="label"
          sortable
          header={<span className="entity-list-panel-column-header">{t("recordingUnit.relations.column.unit")}</span>}
          body={(row: ViewRow) => cell(row, (r) => (
            <span
              className="ref-chip ref-chip-link"
              style={refChipStyle("recording-units")}
              role="link"
              tabIndex={0}
              title={t("list.openInOverview")}
              onClick={() => onOpen(r.unit.resourceId, r.unit.label ?? r.unit.resourceId)}
              onKeyDown={(e) => { if (e.key === "Enter") onOpen(r.unit.resourceId, r.unit.label ?? r.unit.resourceId); }}
            >
              {r.unit.label ?? r.unit.resourceId}
            </span>
          ), "6rem")}
        />
        <Column
          header={<span className="entity-list-panel-column-header">{t("recordingUnit.relations.column.type")}</span>}
          body={(row: ViewRow) => cell(row, (r) => {
            const concept = r.unit.qualifier?.concept?.label;
            return (
              <>
                {concept && <span className="ref-chip" style={refChipStyle("concepts")}>{concept}</span>}
                {r.unit.qualifier?.uncertain && (
                  <i className="bi bi-question-circle sia-relation-uncertain" title={t("recordingUnit.graph.dialog.uncertain")} aria-label={t("recordingUnit.graph.dialog.uncertain")} />
                )}
              </>
            );
          })}
        />
        {editor && (
          <Column
            className="entity-list-panel-row-actions-cell"
            headerClassName="entity-list-panel-row-actions-cell"
            header={<span className="entity-list-panel-column-header">{t("list.actionsHeader")}</span>}
            body={(row: ViewRow) => (row.skeleton ? null : (
              <span className="entity-list-panel-cell">
                {isStratigraphy(row.kind) && (
                  <Button type="button" icon="pi pi-pencil" text rounded aria-label={t("recordingUnit.graph.edit")} onClick={(e) => editor.edit(row.kind, row.unit, e.currentTarget)} />
                )}
                <Button type="button" icon="pi pi-trash" text rounded aria-label={t("recordingUnit.graph.delete")} onClick={(e) => editor.remove(row.kind, row.unit, e.currentTarget)} />
              </span>
            ))}
          />
        )}
        <Column key="__filler" className="entity-list-panel-filler-cell" headerClassName="entity-list-panel-filler-cell" header={null} body={() => null} />
      </DataTable>
    </div>
  );
}

