import { beforeEach, describe, expect, it, vi } from "vitest";

const apiFetch = vi.fn();
vi.mock("../api/client", () => ({ apiFetch: (...args: unknown[]) => apiFetch(...args) }));

import { fetchAllValues } from "./multiValues";

describe("fetchAllValues", () => {
  beforeEach(() => apiFetch.mockReset());

  it("returns a complete answer's own values without a request", async () => {
    await expect(fetchAllValues({ values: [1], total: 1, complete: true })).resolves.toEqual([1]);
    expect(apiFetch).not.toHaveBeenCalled();
  });

  it("follows _links.values page after page until the total", async () => {
    const first = Array.from({ length: 200 }, (_, i) => i);
    apiFetch
      .mockResolvedValueOnce({ data: first, meta: { total: 201 } })
      .mockResolvedValueOnce({ data: [200], meta: { total: 201 } });

    const all = await fetchAllValues({ values: [0], total: 201, complete: false, _links: { values: "/api/v1/x" } });

    expect(all).toHaveLength(201);
    expect(apiFetch).toHaveBeenNthCalledWith(1, "/api/v1/x?offset=0&limit=200");
    expect(apiFetch).toHaveBeenNthCalledWith(2, "/api/v1/x?offset=200&limit=200");
  });
});
