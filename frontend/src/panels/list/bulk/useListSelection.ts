import {useCallback, useEffect, useRef} from "react";
import type {EntityTypeConfig, ListParams, PagedResult} from "../../../entities/shared/types";
import {usePaneState} from "../../pane/paneState";
import {isPlaceholderRow} from "../usePagedList";
import type {RowRecord} from "../useEntityListData";

// The page size the list endpoints accept at most (ProjectApiService.MAX_PAGE_SIZE): how the whole
// result set is read back when every row of it is selected.
const RESOLVE_PAGE_SIZE = 200;

// The whole result set is selected, but the rows unticked since.
interface AllSelected {
  excluded: string[];
}

type ResultSetParams = Omit<ListParams, "offset" | "limit" | "fields">;

export interface UseListSelectionOptions {
  config: EntityTypeConfig<unknown, unknown> | undefined;
  // The result set the selection belongs to (search, filters, sort, scope): changing it ends a
  // « select all ».
  params: ResultSetParams;
  // The list's rows as drawn (placeholders where not fetched yet), and the size of the result set.
  rows: RowRecord[];
  totalCount: number;
}

/**
 * A list's selection, as the bulk action bar sees it: the rows ticked one by one, or the whole
 * result set (every row the search and filters match, shown or not) but the rows unticked since.
 */
export interface ListSelection {
  // The whole result set is selected (the header's box).
  all: boolean;
  // How many rows are selected.
  count: number;
  // The rows ticked in view: what the table shows as ticked.
  rows: RowRecord[];
  // Every selected row, the ones not fetched yet included (read from the server page by page).
  resolve: () => Promise<RowRecord[]>;
  // Ticks / unticks rows one by one.
  setRows: (rows: RowRecord[]) => void;
  // The header's box: the whole result set, or nothing.
  setAll: (checked: boolean) => void;
  // Unticks everything.
  clear: () => void;
}

/**
 * The id a row is compared by.
 */
function keyOf(row: RowRecord): string {
  return String(row.id);
}

/**
 * The rows that can be ticked: the fetched ones.
 */
function fetchedRows(rows: readonly RowRecord[]): RowRecord[] {
  return rows.filter((row) => row.id != null && !isPlaceholderRow(row));
}

/**
 * Reads every row of the result set, page by page, through the entity type's own list endpoint
 * (the same params the table uses: same search, filters, sort and scope).
 */
export async function fetchWholeResultSet(
  config: EntityTypeConfig<unknown, unknown>,
  params: ResultSetParams,
): Promise<RowRecord[]> {
  const all: RowRecord[] = [];
  let total = Number.POSITIVE_INFINITY;
  for (let offset = 0; offset < total; offset += RESOLVE_PAGE_SIZE) {
    const page = (await config.api.list({ ...params, offset, limit: RESOLVE_PAGE_SIZE })) as PagedResult<RowRecord>;
    all.push(...page.data);
    // An empty page ends the walk even if the total said more (rows deleted meanwhile).
    total = page.data.length === 0 ? offset : page.totalCount;
  }
  return all;
}

/**
 * The selection of a list (kept in the pane, like the rest of its state). Ticking the header's box
 * selects the whole result set — not only the rows fetched so far — and a change of search, filters,
 * sort or scope lets go of it, since it would no longer be the same rows.
 */
export function useListSelection({ config, params, rows, totalCount }: UseListSelectionOptions): ListSelection {
  const [picked, setPicked] = usePaneState<RowRecord[]>("selection", []);
  const [allSelected, setAllSelected] = usePaneState<AllSelected | null>("selectionAll", null);

  const resultSetKey = JSON.stringify(params);
  const previousKey = useRef(resultSetKey);
  useEffect(() => {
    if (previousKey.current === resultSetKey) return;
    previousKey.current = resultSetKey;
    setAllSelected(null);
  }, [resultSetKey, setAllSelected]);

  const excluded = new Set(allSelected?.excluded ?? []);
  const shownRows = allSelected ? fetchedRows(rows).filter((row) => !excluded.has(keyOf(row))) : picked;

  const setRows = useCallback(
    (next: RowRecord[]) => {
      if (!allSelected) {
        setPicked(next);
        return;
      }
      // Under « select all », what the table reports is the fetched rows still ticked: the other
      // fetched rows are the unticked ones.
      const ticked = new Set(next.map(keyOf));
      const unticked = fetchedRows(rows).filter((row) => !ticked.has(keyOf(row))).map(keyOf);
      const stillExcluded = allSelected.excluded.filter((id) => !ticked.has(id));
      setAllSelected({ excluded: [...new Set([...stillExcluded, ...unticked])] });
    },
    [allSelected, rows, setAllSelected, setPicked],
  );

  const setAll = useCallback(
    (checked: boolean) => {
      setAllSelected(checked ? { excluded: [] } : null);
      setPicked([]);
    },
    [setAllSelected, setPicked],
  );

  const clear = useCallback(() => setAll(false), [setAll]);

  const resolve = useCallback(async (): Promise<RowRecord[]> => {
    if (!allSelected || !config) return picked;
    const skipped = new Set(allSelected.excluded);
    return (await fetchWholeResultSet(config, params)).filter((row) => !skipped.has(keyOf(row)));
    // `params` is identified by its serialized form, resultSetKey.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allSelected, config, picked, resultSetKey]);

  return {
    all: allSelected != null,
    count: allSelected ? Math.max(0, totalCount - excluded.size) : picked.length,
    rows: shownRows,
    resolve,
    setRows,
    setAll,
    clear,
  };
}
