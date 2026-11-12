import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../../api/client";
import {bulkDelete, bulkLinkDocument, bulkPatch, supportsBulk} from "./bulkApi";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

const result = { succeeded: [12], failed: [{ id: 13, status: 409, message: "Relations" }] };

describe("bulk endpoints", () => {
  beforeEach(() => {
    mockedApiFetch.mockReset();
    mockedApiFetch.mockResolvedValue({ data: result });
  });

  it("deletes the elements in one request, with numeric ids", async () => {
    await expect(bulkDelete("recording-units", ["12", 13])).resolves.toEqual(result);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/bulk/delete", {
      method: "POST",
      body: { ids: [12, 13] },
    });
  });

  it("sends the same patch for every element", async () => {
    const patch = { answers: { parents: { add: ["7"] } } };

    await bulkPatch("recording-units", [12, 13], patch);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/recording-units/bulk/patch", {
      method: "POST",
      body: { ids: [12, 13], patch },
    });
  });

  it("links or unlinks a document on every element", async () => {
    await bulkLinkDocument("finds", [12], "88", "UNLINK");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/finds/bulk/documents", {
      method: "POST",
      body: { ids: [12], documentId: 88, mode: "UNLINK" },
    });
  });

  it("knows which collections the server serves in bulk", () => {
    expect(supportsBulk("recording-units")).toBe(true);
    expect(supportsBulk("organizations")).toBe(false);
  });
});
