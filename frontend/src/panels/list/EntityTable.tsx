import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type DragEvent,
  type ReactNode,
  type SyntheticEvent,
} from "react";
import { onGridKeyDown } from "../../components/table/gridNavigation";
import { Column } from "primereact/column";
import {
  DataTable,
  type DataTableSelectionMultipleChangeEvent,
} from "primereact/datatable";
import { TreeTable } from "primereact/treetable";
import type { TreeNode } from "primereact/treenode";
import { Skeleton } from "primereact/skeleton";
import {
  CellEditOverlay,
  type CellEditTarget,
} from "../../components/table/CellEditOverlay";
import {
  ValidationStatusCell,
  type ValidationStatusCellProps,
} from "../../components/table/ValidationStatusCell";
import { getEntityType } from "../../entities/registry";
import type {
  ColumnDef,
  EntityPreview,
  EntityTypeConfig,
  ListParams,
} from "../../entities/types";
import { entityChipStyle, renderAnswerValue } from "../../fields/display";
import { describeIncoherence } from "../../fields/incoherence";
import { entityRowLabel } from "../../fields/optionSources";
import { hasFieldRenderer } from "../../fields/registry";
import { stratigraphyPositionOf } from "../../fields/stratigraphy";
import type { FieldState } from "../../rules";
import { resolveValueBinding, type FieldResource } from "../../fields/types";
import {
  FROZEN_COLUMN_CLASS,
  frozenColumnStyle,
  useFrozenColumnOffsets,
} from "../frozenColumns";
import { rememberListContext } from "../listContext";
import { PAGE_SIZE_OPTIONS } from "../tableState";
import { useWriteMode } from "../writeMode";
import { isFieldPending } from "../usePagedList";
import type { RowRecord } from "./useEntityListData";
import { buildTreeSelectionColumn } from "./treeSelectionColumn";
import type { EntityTableTree } from "./useTreeRows";
import { t } from "../../i18n";

// Placeholder rows shown during a list's very first load, one skeleton per cell — the table keeps
// its header and shape instead of an empty body under a grey loading overlay.
const SKELETON_ROW = "__skeletonRow";
const SKELETON_ROW_COUNT = 8;
const SKELETON_ROWS: RowRecord[] = Array.from(
  { length: SKELETON_ROW_COUNT },
  (_, i) => ({
    id: `__skeleton-${i}`,
    [SKELETON_ROW]: true,
  }),
);

function isSkeletonRow(row: RowRecord): boolean {
  return row[SKELETON_ROW] === true;
}

export interface EntityTableProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  organizationId?: number;
  columns: ColumnDef<RowRecord>[];
  // The catalog field behind each dynamic column, by column key; pinned columns are absent.
  fieldByColumn: Map<string, FieldResource>;
  // The dynamic columns in the user's order — the only ones that can be dragged.
  visibleColumns: string[];
  setVisibleColumns: (visible: string[]) => void;
  rows: RowRecord[];
  isLoading: boolean;
  totalCount: number;
  // The result set the rows belong to (search, sort, filters, scope): a fiche opened from a row
  // walks it with its previous/next arrows.
  params: Omit<ListParams, "offset" | "limit" | "fields">;
  offset: number;
  limit: number;
  search?: string;
  sort?: string;
  onPage: (offset: number, limit: number) => void;
  onSort: (sort: string | undefined) => void;
  // The rules the cells follow (useRowRules): each cell's state, and the labels their messages use.
  rowRules: {
    stateOf: (row: RowRecord, fieldId: string) => FieldState | undefined;
    labelOf: (fieldId: string) => string;
  };
  // Renders a row's actions column and the dialogs they open (useRowActions).
  renderRowActions: (row: RowRecord) => ReactNode;
  selectedRows: RowRecord[];
  onSelectionChange: (rows: RowRecord[]) => void;
  onNavigate?: (entityType: string, id: string | number) => void;
  onOpenOverview?: (
    entityType: string,
    id: string | number,
    preview?: EntityPreview,
  ) => void;
  overviewEntityId?: string | number;
  // A saved cell: the list and any view of the same entity refetch.
  onCellSaved: (row: RowRecord) => void;
  // Only to look at (the preview of a tab being configured): no actions column, no cell editing.
  readOnly?: boolean;
  // The list's tree view: the rows are drawn by a TreeTable (same columns, same row styles), the
  // identifier column carrying the expander; a parent that only leads to a match gets a dashed chip.
  tree?: EntityTableTree;
}

/**
 * The list's table: server-paginated, sortable, with frozen leading columns, draggable dynamic
 * columns and click-to-edit cells (a DataTable, or a TreeTable in the tree view). Presentational — every piece of data and state comes in through
 * props. The PrimeReact workarounds it needs are each commented where they are applied.
 */
export function EntityTable({
  entityType,
  config,
  organizationId,
  columns,
  fieldByColumn,
  visibleColumns,
  setVisibleColumns,
  rows,
  isLoading,
  totalCount,
  params,
  offset,
  limit,
  search,
  sort,
  onPage,
  onSort,
  rowRules,
  renderRowActions,
  selectedRows,
  onSelectionChange,
  onNavigate,
  onOpenOverview,
  overviewEntityId,
  onCellSaved,
  readOnly,
  tree,
}: EntityTableProps) {
  const tableRef = useRef<DataTable<RowRecord[]>>(null);
  // The table's root, whichever component draws it (display:contents, so it has no box of its own).
  const rootRef = useRef<HTMLDivElement>(null);
  // The one shared edit surface for every editable cell (plan phase 4b) — a single instance,
  // re-anchored (to the clicked cell's own rect) and re-seeded per click, not one per cell/column.
  const [editTarget, setEditTarget] =
    useState<CellEditTarget<RowRecord> | null>(null);
  const writeMode = useWriteMode();
  const canPatchAnswers = config.api.patchAnswers != null;

  // A new page (or a new result set, which goes back to the first one) starts at the top of the
  // table's own scroll box: the scroll position belonged to the rows that were there before.
  const pageKey = `${JSON.stringify(params)}|${offset}|${limit}`;
  useEffect(() => {
    const wrapper = rootRef.current?.querySelector(
      ".p-datatable-wrapper, .p-treetable-wrapper",
    );
    if (wrapper) wrapper.scrollTop = 0;
  }, [pageKey]);

  // PrimeReact workaround #1 — column order. It keeps its own column order once a header has been
  // dragged, and from then on sorts the children by it: a column shown later would land at the far
  // end, and a reorder done in the chooser would be ignored. Re-syncing it to the children after
  // every change of columns keeps `visibleColumns` the one source of truth for the order.
  const columnOrderSignature = columns.map((c) => c.key).join(",");
  useLayoutEffect(() => {
    tableRef.current?.resetColumnOrder();
  }, [columnOrderSignature]);
  // The frozen set follows the columns (a scoped list drops its unscopedOnly ones, so the signature
  // changes with them).
  useFrozenColumnOffsets(
    () => rootRef.current,
    `${columnOrderSignature}|${tree ? "tree" : "flat"}`,
  );

  // entityDataTable.xhtml gates an editable cell on isRendered(col, "writeMode", item), which is
  // the app's global read/write switch AND canUserEditRow(item) — the same two-part rule the
  // fiche and the panel header follow. Only a UI gate: ProjectApiService.patchProject is what
  // actually enforces it; this just avoids offering a control that would 403.
  function canEditRow(row: RowRecord): boolean {
    if (!writeMode || readOnly) return false;
    const permissions = (row as { _permissions?: { canEdit?: boolean } })
      ._permissions;
    return permissions?.canEdit === true;
  }

  // The app's write mode and the row's own right, then the field itself — a read-only one (the
  // row's project, a generated identifier) and a type with no editor (action code, address) are
  // never edited.
  function isEditable(field: FieldResource, row: RowRecord): boolean {
    // A unit's stratigraphy is no answer to patch (its field is read-only): writing it is the unit's own right.
    if (stratigraphyPositionOf(field)) return canEditRow(row);
    return (
      canPatchAnswers &&
      canEditRow(row) &&
      !field.readOnly &&
      hasFieldRenderer(field.answerType)
    );
  }

  // Opens the shared editor for one (row, field), anchored to the clicked cell's own rect so it
  // renders on top of that cell rather than as a popover below it — and, critically, stops the
  // click from bubbling to DataTable's onRowClick, which is how a click on an editable cell
  // doesn't also navigate away (the plan's "collision" between row-click-navigates and
  // click-to-edit).
  function openCellEditor(
    e: SyntheticEvent,
    row: RowRecord,
    field: FieldResource,
  ) {
    e.stopPropagation();
    // Anchored on the <td>, not on the clicked span: the span is only as wide as its text, so
    // anchoring to it would open an editor narrower than the cell it replaces (and offset from it
    // by the cell's padding).
    const target = e.currentTarget as HTMLElement;
    const cell = target.closest("td") ?? target;
    const state = rowRules.stateOf(row, field.id);
    setEditTarget({
      row,
      field,
      anchor: cell.getBoundingClientRect(),
      // A cell the rules disable is shown, not edited — but if it holds a value the rules made
      // incoherent, it can still be cleared (never cleared for the user).
      readOnly: !isEditable(field, row) || state?.enabled === false,
      fieldState: state,
      incoherences: (state?.incoherent ?? []).map((r) =>
        describeIncoherence(r, rowRules.labelOf),
      ),
      canClear: isEditable(field, row),
      fieldLabelOf: rowRules.labelOf,
    });
  }

  function onPageChange(e: { first?: number; rows?: number }) {
    const size = e.rows ?? limit;
    // Keeps the offset a multiple of the page size (the server rejects any other): a size change
    // lands on the page holding the first row that was shown.
    onPage(Math.floor((e.first ?? 0) / size) * size, size);
  }

  // With onSort set, PrimeReact treats sorting as controlled: it reads sortField/sortOrder back from
  // props to compute the next click (asc → desc) and to draw the header arrow. Without them it
  // always sees "no current sort", so every click re-emits the same asc sort and nothing changes.
  const sortSep = sort?.lastIndexOf(":") ?? -1;
  const sortField = sort
    ? sortSep >= 0
      ? sort.slice(0, sortSep)
      : sort
    : undefined;
  const sortOrder = sort
    ? sortSep >= 0 && sort.slice(sortSep + 1) === "desc"
      ? -1
      : 1
    : undefined;

  function onSortChange(e: {
    sortField?: string | null;
    sortOrder?: number | null;
  }) {
    onSort(
      e.sortField
        ? `${e.sortField}:${e.sortOrder === 1 ? "asc" : "desc"}`
        : undefined,
    );
  }

  // Only the identifier cell is clickable (plan §8 phase 5), matching JSF's own CommandLinkColumn
  // — the rest of the row is inert. Opens the overview (JSF's own row-click target); onNavigate is
  // the fallback for a caller with no overview pane at all.
  function openIdentifier(e: SyntheticEvent, row: RowRecord) {
    e.stopPropagation();
    if (row.id == null) return;
    // The fiche's previous/next arrows walk THIS list — its sort, filters, search and scope — from
    // this row's position (panels/listContext.ts).
    const at = rows.findIndex((r) => String(r.id) === String(row.id));
    if (at >= 0)
      rememberListContext(entityType, row.id, { params, index: offset + at });
    if (onOpenOverview) {
      // The row already holds the label and status: the overview's header shows them right away.
      const preview = {
        label: entityRowLabel(row as Parameters<typeof entityRowLabel>[0]),
        validationStatus: (row as { validationStatus?: string | null })
          .validationStatus,
      };
      onOpenOverview(entityType, row.id, preview);
    } else onNavigate?.(entityType, row.id);
  }

  // Same target preference as openIdentifier, for a column linking to another entity.
  function openLinked(
    e: SyntheticEvent,
    targetType: string,
    id: string | number,
  ) {
    e.stopPropagation();
    if (onOpenOverview) onOpenOverview(targetType, id);
    else onNavigate?.(targetType, id);
  }

  // A header dragged in the table: the dynamic columns' new order becomes the visible-columns order
  // (and so what the chooser shows, and what's remembered). Pinned columns keep their place.
  function onColReorder(e: { columns: unknown[] }) {
    const dynamic = new Set(visibleColumns);
    const order = e.columns
      .map(
        (col) => (col as { props?: { columnKey?: string } }).props?.columnKey,
      )
      .filter((key): key is string => key != null && dynamic.has(key));
    setVisibleColumns(order);
  }

  // PrimeReact workaround #2 — frozen headers. It only checks that the DRAGGED column is
  // reorderable, never the drop target, so a column dropped on the frozen block would land inside
  // it (between identifier and actions). Swallowing drag events over a frozen header, in the
  // capture phase before the header's own handler, keeps the frozen block closed.
  function blockDropOnFrozenHeader(e: DragEvent) {
    if ((e.target as Element).closest?.(`th.${FROZEN_COLUMN_CLASS}`))
      e.stopPropagation();
  }

  // The cell's one value element. An editable column renders its value inside the click-to-edit
  // box; everything else gets the plain value slot. The permission check is per ROW
  // (_permissions.canEdit), and it is only a UI gate — the API is what actually enforces it.
  function renderCellValue(col: ColumnDef<RowRecord>, row: RowRecord) {
    if (col.identifier) {
      return (
        <span
          className="entity-list-panel-identifier-link entity-list-panel-row-identifier entity-nav-chip"
          style={entityChipStyle(entityType)}
          role="button"
          tabIndex={0}
          title={onOpenOverview ? t("list.openInOverview") : undefined}
          onClick={(e) => openIdentifier(e, row)}
        >
          <i className={config.icon} aria-hidden="true" />
          <span className="entity-nav-chip-label">{col.render(row)}</span>
        </span>
      );
    }

    if (col.link) {
      const target = col.link(row);
      if (!target) return <span className="entity-list-panel-cell-value" />;
      return (
        <span
          className="entity-list-panel-identifier-link entity-nav-chip"
          style={entityChipStyle(target.entityType)}
          role="button"
          tabIndex={0}
          onClick={(e) => openLinked(e, target.entityType, target.id)}
        >
          <i
            className={getEntityType(target.entityType)?.icon ?? "bi bi-link"}
            aria-hidden="true"
          />
          <span className="entity-nav-chip-label">{col.render(row)}</span>
        </span>
      );
    }

    const field = fieldByColumn.get(col.key);
    const content = col.render(row);
    const state = field ? rowRules.stateOf(row, field.id) : undefined;
    const disabledByRules = state?.enabled === false;
    const incoherences = (state?.incoherent ?? []).map((r) =>
      describeIncoherence(r, rowRules.labelOf),
    );
    const editable =
      field != null && isEditable(field, row) && !disabledByRules;
    // An empty cell nobody can edit has nothing to open.
    if (!field || (!editable && !content)) {
      return (
        <span
          className={`entity-list-panel-cell-value${disabledByRules ? " entity-list-panel-cell-disabled" : ""}`}
          title={disabledByRules ? t("list.disabledByRules") : undefined}
        >
          {content}
        </span>
      );
    }

    // One gesture for every field cell: the first click opens the overlay — its editor, or the
    // value itself when it can't be edited — and the chips there open the referenced fiches.
    return (
      <span
        className={`entity-list-panel-editable-cell${disabledByRules ? " entity-list-panel-cell-disabled" : ""}${
          incoherences.length > 0 ? " entity-list-panel-cell-incoherent" : ""
        }`}
        role="button"
        tabIndex={0}
        // The cell is one line and may be truncated, so the full text is always reachable as the
        // native tooltip; an empty cell falls back to naming what clicking it would edit. A value
        // the rules made incoherent says why first.
        title={
          incoherences.length > 0
            ? incoherences.join("\n")
            : renderAnswerValue(
                field,
                resolveValueBinding(field).readRaw(row),
              ) || t("list.editCell", { label: field.label })
        }
        onClick={(e) => openCellEditor(e, row, field)}
      >
        {incoherences.length > 0 && (
          <i
            className="bi bi-exclamation-triangle-fill entity-list-panel-cell-warning"
            aria-label={t("list.incoherent")}
          />
        )}
        {/* An empty cell shows nothing: a non-breaking space keeps it one line tall, so the whole cell
            stays a click target (the hover outline and the tooltip say it is editable). */}
        {content || (
          <span className="entity-list-panel-editable-cell-empty">{" "}</span>
        )}
      </span>
    );
  }

  // Everything up to and including the identifier column stays put while the rest scrolls
  // sideways — the identifier is how you tell one row from another, so losing it is what makes a
  // wide table unreadable. Driven by ColumnDef.identifier, the flag that already marks that column
  // (the one whose cell navigates), so no entity type has to restate which column this is.
  // -1 (no entity declares an identifier column) freezes nothing, including the selection box.
  // Frozen with frozenColumns.ts, not PrimeReact's Column `frozen` (see there why): the frozen
  // columns are the selection box, the columns up to the identifier, then the row actions.
  const lastFrozenIndex = columns.findIndex((col) => col.identifier);
  const frozenProps = (position: number) => ({
    className: FROZEN_COLUMN_CLASS,
    headerClassName: FROZEN_COLUMN_CLASS,
    style: frozenColumnStyle(position),
  });

  const rowOf = (arg: unknown): RowRecord =>
    tree ? ((arg as TreeNode).data as RowRecord) : (arg as RowRecord);
  const rowStateClasses = (row: RowRecord): string[] => {
    const classes: string[] = [];
    if (tree?.isContext(row)) classes.push("entity-tree-context-row");
    if (overviewEntityId != null && row.id === overviewEntityId)
      classes.push("overview-open");
    if (search && !tree?.isContext(row)) classes.push("search-match");
    return classes;
  };

  const treeSelectionColumn = buildTreeSelectionColumn({
    nodes: tree?.nodes ?? [],
    expandedKeys: tree?.expandedKeys ?? {},
    rows,
    selectedRows,
    onSelectionChange,
    rowOf,
    isSkeleton: isSkeletonRow,
    frozen: lastFrozenIndex >= 0 ? frozenProps(0) : undefined,
  });

  const filler = (
    <Column
      key="__filler"
      columnKey="__filler"
      reorderable={false}
      className="entity-list-panel-filler-cell"
      headerClassName="entity-list-panel-filler-cell"
      header={null}
      body={() => null}
    />
  );

  // An array, not a fragment: both tables read their columns from their direct children.
  const columnElements = [
    columns.flatMap((col, index) => [
      <Column
        key={col.key}
        columnKey={col.key}
        field={col.key}
        {...(index <= lastFrozenIndex ? frozenProps(index + 1) : {})}
        // Only scrolling dynamic columns move: dragging one into or out of the frozen block
        // would desync `frozen` from the column's position, and the order that's kept (and
        // shown in the chooser) is the dynamic columns' — a pinned one dragged elsewhere would
        // snap back.
        reorderable={
          !tree && index > lastFrozenIndex && visibleColumns.includes(col.key)
        }
        // Header text is always one line, truncated with an ellipsis past the CSS max-width
        // (.entity-list-panel-column-header); the full label stays available as the title
        // tooltip. A wrapping header makes every row in the table taller for one long label.
        header={
          <span className="entity-list-panel-column-header" title={col.header}>
            {/* The field's own type icon before its name, as on the fiche. */}
            {fieldByColumn.get(col.key)?.icon && (
              <i className={`${fieldByColumn.get(col.key)!.icon} entity-list-panel-column-icon`} aria-hidden="true" />
            )}
            {col.header}
          </span>
        }
        sortable={col.sortable}
        sortField={col.fieldId ?? col.key}
        // The tree view's expander lives in the identifier cell.
        {...(tree && col.identifier ? { expander: true } : {})}
        // The row's validation state renders in the identifier's cell but outside the
        // clickable chip, exactly like JSF's merged statusIdActionsCol — `validationStatus` is a
        // root property of every entity (TraceableEntity), so every list shows it; a picker
        // when the user may change it. Every cell goes through the same wrapper, so no cell
        // is ever more than one line: .entity-list-panel-cell is the flex row (validation
        // state + value) and the value slot is the part that truncates with an ellipsis.
        // Exactly one element fills that slot — the identifier chip, the editable box, or a
        // plain value — never two nested, because the editable box has to reach out to the
        // cell's padding edge and a clipping wrapper around it would cut that off.
        body={(arg: unknown) => {
          const row = rowOf(arg);
          // A column just shown, still being fetched for this row: a skeleton, not an empty cell.
          return isSkeletonRow(row) || isFieldPending(row, col.key) ? (
            <Skeleton height="1rem" width={col.identifier ? "6rem" : "70%"} />
          ) : (
            <span className="entity-list-panel-cell">
              {col.identifier && (
                <ValidationStatusCell
                  entityType={entityType}
                  collectionPath={config.collectionPath}
                  row={row as ValidationStatusCellProps["row"]}
                />
              )}
              {renderCellValue(col, row)}
            </span>
          );
        }}
      />,
      // The row's actions (bookmark, duplicate, new child…) sit right after the identifier,
      // frozen with it — JSF merges both into one statusIdActionsCol.
      ...(index === Math.max(lastFrozenIndex, 0) && !readOnly
        ? [
            <Column
              key="__row-actions"
              columnKey="__row-actions"
              reorderable={false}
              {...(lastFrozenIndex >= 0
                ? frozenProps(lastFrozenIndex + 2)
                : {})}
              className={`entity-list-panel-row-actions-cell${lastFrozenIndex >= 0 ? ` ${FROZEN_COLUMN_CLASS}` : ""}`}
              headerClassName={`entity-list-panel-row-actions-cell${lastFrozenIndex >= 0 ? ` ${FROZEN_COLUMN_CLASS}` : ""}`}
              header={
                <span className="entity-list-panel-column-header">
                  {t("list.actionsHeader")}
                </span>
              }
              body={(arg: unknown) =>
                isSkeletonRow(rowOf(arg)) ? null : renderRowActions(rowOf(arg))
              }
            />,
          ]
        : []),
    ]),
    filler,
  ];

  return (
    <>
      {/* display:contents: no layout box, the panel's flex chain runs straight through. */}
      <div
        ref={rootRef}
        className="sia-contents"
        onDragOverCapture={blockDropOnFrozenHeader}
        onDropCapture={blockDropOnFrozenHeader}
        onKeyDown={onGridKeyDown}
      >
        {tree ? (
          // Not `scrollable`: that mode splits the header and the body into two tables, which the
          // frozen columns and the sticky header (main-panel.css) can't span. The wrapper scrolls.
          <TreeTable
            value={
              isLoading
                ? SKELETON_ROWS.map((r) => ({
                    key: String(r.id),
                    data: r,
                    leaf: true,
                  }))
                : tree.nodes
            }
            lazy
            paginator
            first={offset}
            rows={limit}
            rowsPerPageOptions={PAGE_SIZE_OPTIONS}
            totalRecords={totalCount}
            onPage={onPageChange}
            onSort={onSortChange}
            sortField={sortField}
            sortOrder={sortOrder}
            sortMode="single"
            expandedKeys={tree.expandedKeys}
            onToggle={(e) => tree.onToggle(e.value)}
            onExpand={(e) => tree.onExpand(e.node)}
            rowClassName={(node: TreeNode) => {
              const row = node.data as RowRecord;
              if (isSkeletonRow(row))
                return { "entity-list-panel-skeleton-row": true };
              return Object.fromEntries(
                rowStateClasses(row).map((c) => [c, true]),
              );
            }}
          >
            {treeSelectionColumn}
            {columnElements}
          </TreeTable>
        ) : (
          <DataTable
            value={isLoading ? SKELETON_ROWS : rows}
            // pages/shared/table/entityDataTable.xhtml is size="small" too — the React table had been
            // left at the theme's default (1rem cell padding vs 0.5rem), which is a big part of why it
            // read as much airier than the JSF one. entity-list-panel's own CSS tightens the vertical
            // padding further on top of this.
            size="small"
            ref={tableRef}
            lazy
            reorderableColumns
            // Server-side paging: `value` is just the current page, totalRecords the whole result set.
            paginator
            first={offset}
            rows={limit}
            rowsPerPageOptions={PAGE_SIZE_OPTIONS}
            totalRecords={totalCount}
            onPage={onPageChange}
            // PrimeReact workaround #3 — it memoizes each cell on its row data, so a cell would ignore
            // anything else its body depends on (the action bar layout, write mode, the columns being
            // fetched). A page is at most 200 rows, so re-rendering them is affordable; the typing
            // that used to trigger it now stays inside ListSearchBox.
            cellMemo={false}
            onColReorder={onColReorder}
            onSort={onSortChange}
            sortField={sortField}
            sortOrder={sortOrder}
            sortMode="single"
            dataKey="id"
            // "overview-open" mirrors EntityTableViewModel.getRowStyleClass's own row highlight for
            // the entity currently shown in the overview pane; "search-match" mirrors the same
            // method's search-hit class — every currently-visible row already matched the server-side
            // search (plan §8 phase 5), except the tree view's context parents (rowStateClasses).
            rowClassName={(row: RowRecord) =>
              isSkeletonRow(row)
                ? "entity-list-panel-skeleton-row"
                : rowStateClasses(row).join(" ")
            }
            selectionMode="checkbox"
            selection={selectedRows}
            onSelectionChange={(
              e: DataTableSelectionMultipleChangeEvent<RowRecord[]>,
            ) => onSelectionChange(e.value)}
            // Sticky header + frozen columns both require this; it also moves the scrolling from the
            // page onto the table's own body, which is the rule this shell is built on (the app is a
            // fixed 100vh frame — every panel scrolls inside itself, the document never does).
            // scrollHeight="flex" means "take the height my parent gives me"; the flex chain that
            // actually gives it one is in main-panel.css.
            scrollable
            scrollHeight="flex"
          >
            {/* The select-all box alone: the selected count lives in the toolbar (see its end group),
              where it doesn't widen the frozen first column. */}
            <Column
              columnKey="__selection"
              selectionMode="multiple"
              {...(lastFrozenIndex >= 0 ? frozenProps(0) : {})}
              headerStyle={{ width: "3rem" }}
              headerClassName={`entity-list-panel-selection-header${lastFrozenIndex >= 0 ? ` ${FROZEN_COLUMN_CLASS}` : ""}`}
              reorderable={false}
            />
            {columnElements}
          </DataTable>
        )}
      </div>
      {canPatchAnswers && (
        <CellEditOverlay
          target={editTarget}
          organizationId={organizationId}
          entityType={entityType}
          onSave={(id, answers) => config.api.patchAnswers!(id, answers)}
          onSaved={() => editTarget && onCellSaved(editTarget.row)}
          onClose={() => setEditTarget(null)}
        />
      )}
    </>
  );
}
