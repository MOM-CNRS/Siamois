import { Checkbox } from "primereact/checkbox";
import { Column } from "primereact/column";
import type { TreeNode } from "primereact/treenode";
import type { TreeTableExpandedKeysType } from "primereact/treetable";
import { FROZEN_COLUMN_CLASS } from "../frozenColumns";
import type { RowRecord } from "./useEntityListData";

// The tree view has no PrimeReact selection (a TreeTable's would tick whole branches): a plain checkbox
// per row, keyed on the row's place in the tree (an entity under two parents is two rows).
const treeKeyOf = (row: RowRecord) =>
  (row as { __treeKey?: string }).__treeKey ?? String(row.id);

function rowsOnScreen(
  nodes: TreeNode[],
  expandedKeys: TreeTableExpandedKeysType,
): RowRecord[] {
  return nodes.flatMap((n) => [
    n.data as RowRecord,
    ...(n.children && expandedKeys[n.key as string]
      ? rowsOnScreen(n.children, expandedKeys)
      : []),
  ]);
}

interface TreeSelectionColumnOptions {
  nodes: TreeNode[];
  expandedKeys: TreeTableExpandedKeysType;
  // The rows on screen, for the select-all box's state.
  rows: RowRecord[];
  selectedRows: RowRecord[];
  onSelectionChange: (rows: RowRecord[]) => void;
  // A column callback's argument is the node: the row is its data.
  rowOf: (arg: unknown) => RowRecord;
  isSkeleton: (row: RowRecord) => boolean;
  // The frozen-column props of the first column, when the table freezes its leading columns.
  frozen?: {
    className: string;
    headerClassName: string;
    style: React.CSSProperties;
  };
}

/** The selection column of the tree view; a function and not a component because TreeTable reads its columns' props off its children. */
export function buildTreeSelectionColumn({
  nodes,
  expandedKeys,
  rows,
  selectedRows,
  onSelectionChange,
  rowOf,
  isSkeleton,
  frozen,
}: TreeSelectionColumnOptions) {
  const frozenClass = frozen ? ` ${FROZEN_COLUMN_CLASS}` : "";
  return (
    <Column
      columnKey="__selection"
      {...(frozen ? { style: frozen.style } : {})}
      headerStyle={{ width: "3rem" }}
      className={`p-selection-column${frozenClass}`}
      headerClassName={`p-selection-column entity-list-panel-selection-header${frozenClass}`}
      reorderable={false}
      header={
        <Checkbox
          checked={rows.length > 0 && selectedRows.length === rows.length}
          onChange={(e) =>
            onSelectionChange(
              e.checked ? rowsOnScreen(nodes, expandedKeys) : [],
            )
          }
        />
      }
      body={(arg: unknown) => {
        const row = rowOf(arg);
        if (isSkeleton(row)) return null;
        return (
          <Checkbox
            checked={selectedRows.some((r) => treeKeyOf(r) === treeKeyOf(row))}
            onChange={(e) =>
              onSelectionChange(
                e.checked
                  ? [...selectedRows, row]
                  : selectedRows.filter((r) => treeKeyOf(r) !== treeKeyOf(row)),
              )
            }
          />
        );
      }}
    />
  );
}
