import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {apiFetch} from "../../api/client";
import {BULK_CHUNK_SIZE, BULK_POLL_FIRST_MS, BULK_POLL_MAX_MS, createBulkApi, followBulkJob} from "./bulkApi";

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

  it("starts a delete job with numeric ids and hands back what to follow it by", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("RUNNING", 0));

    await expect(createBulkApi("recording-units").delete(["12", 13])).resolves.toEqual([{ id: "job-1", total: 2 }]);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/recording-units/delete", {
      method: "POST",
      body: { ids: [12, 13] },
    });
  });

  it("starts a patch job with the same patch for every element", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("RUNNING", 0));
    const patch = { answers: { parents: { add: ["7"] } } };

    await createBulkApi("recording-units").patch([12, 13], patch);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/recording-units/patch", {
      method: "POST",
      body: { ids: [12, 13], patch },
    });
  });

  it("links or unlinks a document on every element of a type documents link to", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("RUNNING", 0));

    await createBulkApi("finds", { linksDocuments: true }).linkDocument!([12], "88", "UNLINK");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/finds/documents", {
      method: "POST",
      body: { ids: [12], documentId: 88, mode: "UNLINK" },
    });
  });

  it("adds the same stratigraphic relationship to every unit", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("RUNNING", 0));

    await createBulkApi("recording-units", { linksStratigraphy: true }).linkStratigraphy!(
      ["12"],
      { relatedRecordingUnitId: "42", position: "anterior", typeConceptId: "9", uncertain: true },
      "LINK",
    );

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/recording-units/stratigraphy", {
      method: "POST",
      body: { ids: [12], relatedRecordingUnitId: 42, position: "anterior", typeConceptId: 9, uncertain: true, mode: "LINK" },
    });
  });

  it("offers no stratigraphy for a type without it", () => {
    expect(createBulkApi("finds", { linksDocuments: true }).linkStratigraphy).toBeUndefined();
  });

  it("offers no document link for a type documents don't link to", () => {
    expect(createBulkApi("studies").linkDocument).toBeUndefined();
  });

  it("splits a very large selection into several jobs", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("RUNNING", 0)).mockResolvedValueOnce(job("RUNNING", 0, { id: "job-2" }));
    const ids = Array.from({ length: BULK_CHUNK_SIZE + 1 }, (_, i) => i + 1);

    const refs = await createBulkApi("finds").delete(ids);

    expect(refs.map((ref) => ref.id)).toEqual(["job-1", "job-2"]);
  });
});

describe("followBulkJob", () => {
  beforeEach(() => {
    mockedApiFetch.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("follows a running job, reporting its progress, until the server is done", async () => {
    vi.useFakeTimers();
    mockedApiFetch
      .mockResolvedValueOnce(job("RUNNING", 0))
      .mockResolvedValueOnce(job("RUNNING", 1))
      .mockResolvedValueOnce(job("DONE", 2, { succeeded: [12], failed: [{ id: 13, status: 403, message: "Interdit" }] }));
    const onProgress = vi.fn();

    const pending = followBulkJob("job-1", onProgress);
    await vi.advanceTimersByTimeAsync(BULK_POLL_MAX_MS * 2);

    await expect(pending).resolves.toEqual({ succeeded: [12], failed: [{ id: 13, status: 403, message: "Interdit" }] });
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/bulk/jobs/job-1");
    expect(onProgress.mock.calls.map(([p]) => p.processed)).toEqual([0, 1, 2]);
  });

  it("asks less and less often while the job runs", async () => {
    vi.useFakeTimers();
    mockedApiFetch.mockResolvedValue(job("RUNNING", 0));

    void followBulkJob("job-1");
    await vi.advanceTimersByTimeAsync(0);
    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(BULK_POLL_FIRST_MS);
    expect(mockedApiFetch).toHaveBeenCalledTimes(2);
    // The second wait is longer than the first: half a second more brings no new request.
    await vi.advanceTimersByTimeAsync(BULK_POLL_FIRST_MS);
    expect(mockedApiFetch).toHaveBeenCalledTimes(2);
  });

  it("rejects when the job stopped on an error", async () => {
    mockedApiFetch.mockResolvedValueOnce(job("FAILED", 1, { error: "Base indisponible" }));

    await expect(followBulkJob("job-1")).rejects.toThrow("Base indisponible");
  });
});
