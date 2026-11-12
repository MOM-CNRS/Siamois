import {describe, expect, it} from "vitest";
import {ApiError} from "../../../api/client";
import {runBulk} from "./runBulk";

describe("runBulk", () => {
  it("runs the task once per id and reports every success", async () => {
    const seen: (string | number)[] = [];

    const outcome = await runBulk([1, 2, 3], async (id) => {
      seen.push(id);
    }, "fallback");

    expect(seen.sort()).toEqual([1, 2, 3]);
    expect(outcome.succeeded.sort()).toEqual([1, 2, 3]);
    expect(outcome.failed).toEqual([]);
  });

  it("keeps going past a failure and names it with the server's message", async () => {
    const outcome = await runBulk([1, 2, 3], async (id) => {
      if (id === 2) throw new ApiError(409, "Cette UE a des relations");
    }, "fallback");

    expect(outcome.succeeded.sort()).toEqual([1, 3]);
    expect(outcome.failed).toEqual([{ id: 2, message: "Cette UE a des relations" }]);
  });

  it("never has more tasks in flight than allowed", async () => {
    let inFlight = 0;
    let peak = 0;

    await runBulk(Array.from({ length: 10 }, (_, i) => i), async () => {
      inFlight += 1;
      peak = Math.max(peak, inFlight);
      await new Promise((resolve) => setTimeout(resolve, 1));
      inFlight -= 1;
    }, "fallback", 3);

    expect(peak).toBe(3);
  });

  it("does nothing for an empty selection", async () => {
    const outcome = await runBulk([], async () => undefined, "fallback");

    expect(outcome).toEqual({ succeeded: [], failed: [] });
  });
});
