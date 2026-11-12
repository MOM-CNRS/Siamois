import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {ApiError} from "../../../api/client";
import {type BulkResult, followBulkJob} from "../../../entities/shared/bulkApi";
import {notifySpy, taskToastSpy} from "../../../test/notifySpy";
import {BulkJobNotifier} from "./BulkJobNotifier";
import {bulkJobsSnapshot, trackBulkJob} from "./bulkJobTracker";

vi.mock("../../../entities/shared/bulkApi", () => ({ followBulkJob: vi.fn() }));
const mockedFollow = vi.mocked(followBulkJob);

let container: HTMLDivElement;
let root: Root;

/**
 * A job the test ends when it wants: its progress callback, and how to finish it.
 */
function controllableJob() {
  let progress: ((p: { processed: number; total: number }) => void) | undefined;
  let finish: (result: BulkResult) => void = () => {};
  let fail: (error: unknown) => void = () => {};
  mockedFollow.mockImplementation((_id, onProgress) => {
    progress = onProgress;
    return new Promise<BulkResult>((resolve, reject) => {
      finish = resolve;
      fail = reject;
    });
  });
  return {
    progress: (processed: number, total: number) => progress?.({ processed, total }),
    finish: (result: BulkResult) => finish(result),
    fail: (error: unknown) => fail(error),
  };
}

/**
 * Lets the promises settle.
 */
async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

beforeEach(() => {
  mockedFollow.mockReset();
  window.sessionStorage.clear();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient()}>
        <BulkJobNotifier />
      </QueryClientProvider>,
    );
  });
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("BulkJobNotifier", () => {
  it("shows a job's progress in a toast, then what it did", async () => {
    const job = controllableJob();
    act(() => {
      void trackBulkJob({ id: "job-1", label: "bulk.taskDelete", successKey: "bulk.deleteDone" }, 3);
    });

    expect(notifySpy.task).toHaveBeenCalledWith("Suppression en cours");

    act(() => job.progress(2, 3));
    expect(taskToastSpy.progress).toHaveBeenLastCalledWith(2, 3);

    act(() => job.finish({ succeeded: [1, 2], failed: [{ id: 3, status: 409, message: "Relations" }] }));
    await flush();

    expect(taskToastSpy.close).toHaveBeenCalled();
    expect(notifySpy.warn).toHaveBeenCalledWith("Suppression terminée : 2 entités supprimées, 1 en échec. Relations", 8000);
    expect(bulkJobsSnapshot()).toEqual([]);
  });

  it("lets go silently of a job that can no longer be read", async () => {
    const job = controllableJob();
    act(() => {
      trackBulkJob({ id: "job-2", label: "bulk.taskEdit", successKey: "bulk.editDone" }).catch(() => {});
    });

    act(() => job.fail(new ApiError(404, "Tâche introuvable")));
    await flush();

    expect(notifySpy.error).not.toHaveBeenCalled();
    expect(bulkJobsSnapshot()).toEqual([]);
  });

  it("tells how many entities a job changed", async () => {
    const job = controllableJob();
    act(() => {
      void trackBulkJob({ id: "job-3", label: "bulk.taskEdit", successKey: "bulk.editDone" }, 2);
    });

    act(() => job.finish({ succeeded: [1, 2], failed: [] }));
    await flush();

    expect(notifySpy.success).toHaveBeenCalledWith("Modification terminée : 2 entités modifiées", 8000);
  });
});
