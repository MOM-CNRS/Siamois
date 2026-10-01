import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { getProjectHistory } from "./history";

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockClear();
});

describe("getProjectHistory", () => {
  it("fetches by id and unwraps the data envelope, most recent first", async () => {
    const entries = [
      { revisionNumber: 3, revisionDate: "2026-09-01T00:00:00Z", revisionType: "MOD", author: { id: 1, name: "A", lastname: "B" } },
      { revisionNumber: 2, revisionDate: "2026-08-01T00:00:00Z", revisionType: "MOD", author: null },
    ];
    mockedApiFetch.mockResolvedValueOnce({ data: entries });

    const result = await getProjectHistory(5);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5/history");
    expect(result).toEqual(entries);
  });
});
