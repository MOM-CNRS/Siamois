import {describe, expect, it} from "vitest";
import {placeVertically, VIEWPORT_MARGIN} from "./viewportPlacement";

const VIEWPORT = 800;

describe("placeVertically", () => {
  it("opens under the anchor when there is room below it", () => {
    expect(placeVertically({ top: 100, bottom: 130 }, 300, VIEWPORT)).toEqual({ top: 140, side: "below" });
  });

  it("opens over the anchor when it would run off the bottom of the window", () => {
    expect(placeVertically({ top: 700, bottom: 730 }, 300, VIEWPORT)).toEqual({ top: 390, side: "above" });
  });

  it("keeps the bottom margin free when it opens under the anchor", () => {
    const anchor = { top: 400, bottom: 430 };
    const height = VIEWPORT - VIEWPORT_MARGIN - 440;
    expect(placeVertically(anchor, height, VIEWPORT).side).toBe("below");
    expect(placeVertically(anchor, height + 1, VIEWPORT).side).toBe("above");
  });

  it("is pulled back into the window when it fits on neither side of the anchor", () => {
    expect(placeVertically({ top: 400, bottom: 430 }, 500, VIEWPORT)).toEqual({ top: VIEWPORT - VIEWPORT_MARGIN - 500, side: "detached" });
  });

  it("never goes above the top of the window, even taller than it", () => {
    expect(placeVertically({ top: 400, bottom: 430 }, 1000, VIEWPORT)).toEqual({ top: VIEWPORT_MARGIN, side: "detached" });
  });
});
