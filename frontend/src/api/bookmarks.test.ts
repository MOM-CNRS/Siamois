import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "./client";
import { createBookmark, deleteBookmark } from "./bookmarks";

vi.mock("./client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockClear();
});

describe("createBookmark", () => {
  it("POSTs the bookmark request", async () => {
    mockedApiFetch.mockResolvedValueOnce(undefined);

    await createBookmark({ resourceUri: "/action-unit/5", titleCode: "Fouille A", organizationId: 7 });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bookmarks", {
      method: "POST",
      body: { resourceUri: "/action-unit/5", titleCode: "Fouille A", organizationId: 7 },
    });
  });
});

describe("deleteBookmark", () => {
  it("DELETEs with resourceUri and organizationId as query params", async () => {
    mockedApiFetch.mockResolvedValueOnce(undefined);

    await deleteBookmark("/action-unit/5", 7);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bookmarks?resourceUri=%2Faction-unit%2F5&organizationId=7", {
      method: "DELETE",
    });
  });
});
