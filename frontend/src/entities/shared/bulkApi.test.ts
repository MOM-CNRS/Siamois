import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../../api/client";
import {BULK_CHUNK_SIZE, BULK_POLL_INTERVAL_MS, createBulkApi} from "./bulkApi";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

/**
 * A job as the server reports it.
 */
function job(status: "RUNNING" | "DONE" | "FAILED", processed: number, extra: Record<string, unknown> = {}) {
  return { data: { id: "job-1", status, total: 2, processed, succeeded: [], failed: [], ...extra } };
}

describe("createBulkApi", () => {
  beforeEach(() => {
    mockedApiFetch.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("starts a delete job with numeric ids and resolves with its result once done", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("DONE", 2, { succeeded: [12, 13] }));

    await expect(createBulkApi("recording-units").delete(["12", 13])).resolves.toEqual({ succeeded: [12, 13], failed: [] });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/recording-units/delete", {
      method: "POST",
      body: { ids: [12, 13] },
    });
  });

  it("follows a running job, reporting its progress, until the server is done", async () => {
    vi.useFakeTimers();
    mockedApiFetch
      .mockResolvedValueOnce(job("RUNNING", 0))
      .mockResolvedValueOnce(job("RUNNING", 1))
      .mockResolvedValueOnce(job("DONE", 2, { succeeded: [12], failed: [{ id: 13, status: 403, message: "Interdit" }] }));
    const onProgress = vi.fn();

    const pending = createBulkApi("recording-units").patch([12, 13], { answers: {} }, onProgress);
    await vi.advanceTimersByTimeAsync(BULK_POLL_INTERVAL_MS * 2);

    await expect(pending).resolves.toEqual({ succeeded: [12], failed: [{ id: 13, status: 403, message: "Interdit" }] });
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/jobs/job-1");
    expect(onProgress.mock.calls.map(([p]) => p.processed)).toEqual([0, 1, 2]);
  });

  it("rejects when the job stopped on an error", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("FAILED", 1, { error: "Base indisponible" }));

    await expect(createBulkApi("finds").delete([1, 2])).rejects.toThrow("Base indisponible");
  });

  it("links or unlinks a document on every element of a type documents link to", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("DONE", 1, { succeeded: [12] }));

    await createBulkApi("finds", { linksDocuments: true }).linkDocument!([12], "88", "UNLINK");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/finds/documents", {
      method: "POST",
      body: { ids: [12], documentId: 88, mode: "UNLINK" },
    });
  });

  it("offers no document link for a type documents don't link to", () => {
    expect(createBulkApi("studies").linkDocument).toBeUndefined();
  });

  it("splits a very large selection into several jobs and counts the progress over all of it", async () => {
    mockedApiFetch
      .mockResolvedValueOnce(job("DONE", BULK_CHUNK_SIZE, { succeeded: [1] }))
      .mockResolvedValueOnce(job("DONE", 1, { failed: [{ id: BULK_CHUNK_SIZE + 1, status: 403, message: "Interdit" }] }));
    const ids = Array.from({ length: BULK_CHUNK_SIZE + 1 }, (_, i) => i + 1);
    const onProgress = vi.fn();

    const total = await createBulkApi("finds").delete(ids, onProgress);

    expect(mockedApiFetch).toHaveBeenCalledTimes(2);
    expect(total.failed).toHaveLength(1);
    expect(onProgress).toHaveBeenLastCalledWith({ processed: BULK_CHUNK_SIZE + 1, total: BULK_CHUNK_SIZE + 1 });
  });
});
