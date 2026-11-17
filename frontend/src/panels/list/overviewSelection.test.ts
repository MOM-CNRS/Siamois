import {describe, expect, it} from "vitest";
import {isOpenInOverview} from "./overviewSelection";

describe("isOpenInOverview", () => {
  it("matches the overview's entity whether its id is a string or a number", () => {
    expect(isOpenInOverview(12, "12")).toBe(true);
    expect(isOpenInOverview("12", 12)).toBe(true);
    expect(isOpenInOverview(12, "13")).toBe(false);
  });

  it("matches nothing when the overview shows nothing", () => {
    expect(isOpenInOverview(undefined, "12")).toBe(false);
    expect(isOpenInOverview(null, null)).toBe(false);
  });
});
