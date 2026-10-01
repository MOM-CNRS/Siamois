import { beforeEach, describe, expect, it, vi } from "vitest";
import type { EntityTypeConfig } from "../entities/types";
import { fetchListSiblings } from "./listSiblings";

// A list of ten rows, "r0".."r9", served by offset/limit like the real endpoints.
const ROWS = Array.from({ length: 10 }, (_, i) => ({ id: String(100 + i), fullIdentifier: `r${i}` }));

const listMock = vi.fn();
const config = {
  key: "fake",
  routes: { list: "/fake", detail: (id: string | number) => `/fake/${id}` },
  api: { list: listMock },
} as unknown as EntityTypeConfig<any, any>;

function serve(rows: typeof ROWS) {
  listMock.mockImplementation(async (p: { offset: number; limit: number }) => {
    // The real endpoints refuse an offset that isn't a multiple of the page size (a 400).
    if (p.offset % p.limit !== 0) throw new Error("offset doit être un multiple de limit");
    return {
      data: rows.slice(p.offset, p.offset + p.limit),
      totalCount: rows.length,
    };
  });
}

beforeEach(() => {
  listMock.mockReset();
  serve(ROWS);
});

describe("fetchListSiblings", () => {
  it("takes the rows above and below in the list it was opened from, with their positions", async () => {
    const siblings = await fetchListSiblings(config, "104", { params: { sort: "name:asc", search: "x" }, index: 4 });

    expect(siblings).toEqual({
      previous: { id: "103", label: "r3", resourceUri: "/fake/103", index: 3 },
      next: { id: "105", label: "r5", resourceUri: "/fake/105", index: 5 },
    });
    // Single rows of the very same request — sort, search, scope included. Never a window: the
    // endpoints reject an offset that is not a multiple of the page size.
    expect(listMock).toHaveBeenCalledTimes(3);
    expect(listMock).toHaveBeenCalledWith({ sort: "name:asc", search: "x", offset: 4, limit: 1 });
    expect(listMock).toHaveBeenCalledWith({ sort: "name:asc", search: "x", offset: 3, limit: 1 });
    expect(listMock).toHaveBeenCalledWith({ sort: "name:asc", search: "x", offset: 5, limit: 1 });
  });

  it("loops from the first row to the last", async () => {
    const siblings = await fetchListSiblings(config, "100", { params: {}, index: 0 });

    expect(siblings?.previous).toMatchObject({ id: "109", index: 9 });
    expect(siblings?.next).toMatchObject({ id: "101", index: 1 });
    expect(listMock).toHaveBeenCalledWith({ offset: 9, limit: 1 });
  });

  it("loops from the last row to the first", async () => {
    const siblings = await fetchListSiblings(config, "109", { params: {}, index: 9 });

    expect(siblings?.previous).toMatchObject({ id: "108", index: 8 });
    expect(siblings?.next).toMatchObject({ id: "100", index: 0 });
    expect(listMock).toHaveBeenCalledWith({ offset: 0, limit: 1 });
  });

  it("only ever asks for pages the server accepts", async () => {
    await fetchListSiblings(config, "107", { params: {}, index: 7 });

    for (const [request] of listMock.mock.calls) expect(request.offset % request.limit).toBe(0);
  });

  it("gives up, so the default order is used, when a request fails", async () => {
    listMock.mockRejectedValue(new Error("400"));

    expect(await fetchListSiblings(config, "104", { params: {}, index: 4 })).toBeNull();
  });

  it("gives up when the remembered position no longer holds the entity", async () => {
    // The list changed under the fiche: index 4 is r4 now, not the entity being shown.
    expect(await fetchListSiblings(config, "999", { params: {}, index: 4 })).toBeNull();
    // Past the end of a list that shrank.
    expect(await fetchListSiblings(config, "104", { params: {}, index: 40 })).toBeNull();
  });

  it("has no neighbours in a list of one", async () => {
    serve(ROWS.slice(0, 1));

    expect(await fetchListSiblings(config, "100", { params: {}, index: 0 })).toEqual({
      previous: undefined,
      next: undefined,
    });
  });

  it("walks a two-row list both ways", async () => {
    serve(ROWS.slice(0, 2));

    const siblings = await fetchListSiblings(config, "100", { params: {}, index: 0 });
    expect(siblings?.previous).toMatchObject({ id: "101", index: 1 });
    expect(siblings?.next).toMatchObject({ id: "101", index: 1 });
  });
});
