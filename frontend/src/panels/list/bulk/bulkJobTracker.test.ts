import {beforeEach, describe, expect, it, vi} from "vitest";
import {followBulkJob} from "../../../entities/shared/bulkApi";
import {acknowledgeBulkJob, bulkJobsSnapshot, resumeBulkJobs, trackBulkJob} from "./bulkJobTracker";

vi.mock("../../../entities/shared/bulkApi", () => ({ followBulkJob: vi.fn() }));
const mockedFollow = vi.mocked(followBulkJob);

const STORAGE_KEY = "siamois.bulkJobs";

beforeEach(() => {
  mockedFollow.mockReset();
  window.sessionStorage.clear();
});

describe("bulkJobTracker", () => {
  it("follows a job once, shares its progress and outcome, and remembers it for the tab until told", async () => {
    mockedFollow.mockImplementation(async (_id, onProgress) => {
      onProgress?.({ processed: 1, total: 2 });
      return { succeeded: [1, 2], failed: [] };
    });
    const entry = { id: "job-1", label: "bulk.taskDelete" as const, successKey: "bulk.deleted" as const };

    const first = trackBulkJob(entry, 2);
    const second = trackBulkJob(entry, 2);

    await expect(first).resolves.toEqual({ succeeded: [1, 2], failed: [] });
    expect(second).toBe(first);
    expect(mockedFollow).toHaveBeenCalledTimes(1);
    expect(bulkJobsSnapshot()[0].outcome).toEqual({ result: { succeeded: [1, 2], failed: [] } });
    expect(JSON.parse(window.sessionStorage.getItem(STORAGE_KEY)!)).toEqual([entry]);

    acknowledgeBulkJob("job-1");

    expect(bulkJobsSnapshot()).toEqual([]);
    expect(JSON.parse(window.sessionStorage.getItem(STORAGE_KEY)!)).toEqual([]);
  });

  it("goes on following the jobs a previous page of the tab started", () => {
    mockedFollow.mockReturnValue(new Promise(() => {}));
    const entry = { id: "job-9", label: "bulk.taskEdit", successKey: "bulk.done" };
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify([entry]));

    resumeBulkJobs();

    expect(mockedFollow).toHaveBeenCalledWith("job-9", expect.any(Function));
    expect(bulkJobsSnapshot().map((job) => job.id)).toEqual(["job-9"]);
    acknowledgeBulkJob("job-9");
  });
});
