import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import type {EntityTypeConfig, ListParams, PagedResult} from "../../../entities/shared/types";
import {placeholderRow} from "../usePagedList";
import type {RowRecord} from "../useEntityListData";
import {type ListSelection, useListSelection} from "./useListSelection";

const list = vi.fn<(params: ListParams) => Promise<PagedResult<unknown>>>();
const config = { api: { list } } as unknown as EntityTypeConfig<unknown, unknown>;

const fetched: RowRecord[] = [{ id: 1 }, { id: 2 }, placeholderRow(2)];

let container: HTMLDivElement;
let root: Root;
let current: ListSelection;

/**
 * Records the hook's latest value.
 */
function Probe({ params, rows }: { params: Omit<ListParams, "offset" | "limit" | "fields">; rows: RowRecord[] }) {
  current = useListSelection({ config, params, rows, totalCount: 450 });
  return null;
}

/**
 * Renders the hook over these params.
 */
function render(params: Omit<ListParams, "offset" | "limit" | "fields"> = { search: "fosse" }) {
  act(() => root.render(<Probe params={params} rows={fetched} />));
}

beforeEach(() => {
  list.mockReset();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("useListSelection", () => {
  it("counts the rows ticked one by one", () => {
    render();

    act(() => current.setRows([fetched[0]]));

    expect(current.all).toBe(false);
    expect(current.count).toBe(1);
    expect(current.rows).toEqual([fetched[0]]);
  });

  it("selects the whole result set with the header's box, and ticks every fetched row", () => {
    render();

    act(() => current.setAll(true));

    expect(current.all).toBe(true);
    expect(current.count).toBe(450);
    expect(current.rows).toEqual([fetched[0], fetched[1]]);
  });

  it("keeps the rows unticked since out of the whole result set", () => {
    render();
    act(() => current.setAll(true));

    act(() => current.setRows([fetched[1]]));

    expect(current.count).toBe(449);
    expect(current.rows).toEqual([fetched[1]]);
  });

  it("reads the whole result set back page by page, without the unticked rows", async () => {
    list
      .mockResolvedValueOnce({ data: [{ id: 1 }, { id: 2 }], totalCount: 201, limit: 200, offset: 0 })
      .mockResolvedValueOnce({ data: [{ id: 3 }], totalCount: 201, limit: 200, offset: 200 });
    render();
    act(() => current.setAll(true));
    act(() => current.setRows([fetched[1]]));

    const resolved = await current.resolve();

    expect(resolved).toEqual([{ id: 2 }, { id: 3 }]);
    expect(list).toHaveBeenNthCalledWith(1, { search: "fosse", offset: 0, limit: 200 });
    expect(list).toHaveBeenNthCalledWith(2, { search: "fosse", offset: 200, limit: 200 });
  });

  it("lets go of « select all » when the search or filters change", () => {
    render({ search: "fosse" });
    act(() => current.setAll(true));

    render({ search: "mur" });

    expect(current.all).toBe(false);
  });

  it("empties a selection ticked row by row when the filters change, but keeps it on a sort", () => {
    render({ search: "fosse", sort: "identifier:asc" });
    act(() => current.setRows([fetched[0]]));

    render({ search: "fosse", sort: "identifier:desc" });
    expect(current.count).toBe(1);

    render({ search: "mur", sort: "identifier:desc" });
    expect(current.count).toBe(0);
  });
});
