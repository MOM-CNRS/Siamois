import {beforeEach, describe, expect, it, vi} from "vitest";
import {getRelationGraph, getRelationList} from "./api";

const apiFetch = vi.fn();
vi.mock("../../../api/client", () => ({ apiFetch: (...args: unknown[]) => apiFetch(...args) }));

describe("relation graph reads", () => {
  beforeEach(() => apiFetch.mockReset());

  it("reads the graph, enveloped or not", async () => {
    apiFetch.mockResolvedValueOnce({ data: { center: { resourceId: "1" } } }).mockResolvedValueOnce({ center: { resourceId: "2" } });

    expect((await getRelationGraph(5)).center.resourceId).toBe("1");
    expect((await getRelationGraph(5)).center.resourceId).toBe("2");
    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/5/relation-graph");
  });

  it("asks for a page of the list, filtered by kind, searched and sorted", async () => {
    apiFetch.mockResolvedValue({ data: [], meta: { total: 0, limit: 25, offset: 50 } });

    await getRelationList(5, { kinds: ["anterior", "parents"], search: "us 4", sort: "label:desc", offset: 50, limit: 25 });

    const url = new URL(String(apiFetch.mock.calls[0][0]), "http://host");
    expect(url.pathname).toBe("/api/v1/recording-units/5/relations");
    expect(url.searchParams.getAll("kind")).toEqual(["anterior", "parents"]);
    expect(url.searchParams.get("search")).toBe("us 4");
    expect(url.searchParams.get("sort")).toBe("label:desc");
    expect(url.searchParams.get("offset")).toBe("50");
    expect(url.searchParams.get("limit")).toBe("25");
  });

  it("sends neither kind, search nor sort when there are none", async () => {
    apiFetch.mockResolvedValue({ data: [], meta: { total: 0, limit: 25, offset: 0 } });

    await getRelationList(5, { kinds: [], offset: 0, limit: 25 });

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/5/relations?offset=0&limit=25");
  });
});
