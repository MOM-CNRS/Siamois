import {beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../../api/client";
import {BULK_CHUNK_SIZE, createBulkApi} from "./bulkApi";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

const result = { succeeded: [12], failed: [{ id: 13, status: 409, message: "Relations" }] };

describe("createBulkApi", () => {
  beforeEach(() => {
    mockedApiFetch.mockReset();
    mockedApiFetch.mockResolvedValue({ data: result });
  });

  it("deletes the elements in one request, with numeric ids", async () => {
    await expect(createBulkApi("recording-units").delete(["12", 13])).resolves.toEqual(result);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/recording-units/delete", {
      method: "POST",
      body: { ids: [12, 13] },
    });
  });

  it("sends the same patch for every element", async () => {
    const patch = { answers: { parents: { add: ["7"] } } };

    await createBulkApi("recording-units").patch([12, 13], patch);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/recording-units/patch", {
      method: "POST",
      body: { ids: [12, 13], patch },
    });
  });

  it("links or unlinks a document on every element of a type documents link to", async () => {
    await createBulkApi("finds", { linksDocuments: true }).linkDocument!([12], "88", "UNLINK");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/finds/documents", {
      method: "POST",
      body: { ids: [12], documentId: 88, mode: "UNLINK" },
    });
  });

  it("offers no document link for a type documents don't link to", () => {
    expect(createBulkApi("studies").linkDocument).toBeUndefined();
  });

  it("splits a large selection into requests the server accepts, and adds up their results", async () => {
    mockedApiFetch
      .mockResolvedValueOnce({ data: { succeeded: [1], failed: [] } })
      .mockResolvedValueOnce({ data: { succeeded: [], failed: [{ id: 1001, status: 403, message: "Interdit" }] } });
    const ids = Array.from({ length: BULK_CHUNK_SIZE + 1 }, (_, i) => i + 1);

    const total = await createBulkApi("finds").delete(ids);

    expect(mockedApiFetch).toHaveBeenCalledTimes(2);
    expect(total).toEqual({ succeeded: [1], failed: [{ id: 1001, status: 403, message: "Interdit" }] });
  });
});
