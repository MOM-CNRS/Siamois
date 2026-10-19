import { describe, expect, it, vi } from "vitest";
import { announceEntityDeleted, onEntityDeleted } from "./entityDeleted";

describe("entityDeleted", () => {
  it("tells every listener which entity went away, until it unsubscribes", () => {
    const first = vi.fn();
    const second = vi.fn();
    const stopFirst = onEntityDeleted(first);
    const stopSecond = onEntityDeleted(second);

    announceEntityDeleted("phase", 4);
    expect(first).toHaveBeenCalledWith("phase", 4);
    expect(second).toHaveBeenCalledWith("phase", 4);

    stopFirst();
    announceEntityDeleted("phase", 5);
    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(2);
    stopSecond();
  });
});
