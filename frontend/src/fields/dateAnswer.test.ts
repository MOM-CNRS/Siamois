import { describe, expect, it } from "vitest";
import { formatDateAnswer, parseDateAnswer } from "./dateAnswer";

// A date-only answer is a calendar day: it must round-trip through the picker's local Date without
// drifting a day (toISOString() used to turn local midnight in France into the previous day, UTC).
describe("date answers", () => {
  it("reads a yyyy-MM-dd answer as local midnight of that day", () => {
    const date = parseDateAnswer("2024-01-15")!;
    expect([date.getFullYear(), date.getMonth(), date.getDate(), date.getHours()]).toEqual([2024, 0, 15, 0]);
  });

  it("writes the picked local day, not the UTC one", () => {
    expect(formatDateAnswer(new Date(2024, 0, 15), false)).toBe("2024-01-15");
    expect(formatDateAnswer(new Date(2024, 11, 31, 0, 30), false)).toBe("2024-12-31");
  });

  it("round-trips without drifting", () => {
    expect(formatDateAnswer(parseDateAnswer("2025-03-30")!, false)).toBe("2025-03-30");
  });

  it("reads the day of the server's midnight-UTC value, whatever the local offset", () => {
    const date = parseDateAnswer("2024-01-15T00:00:00Z")!;
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2024, 0, 15]);
  });

  it("keeps the instant when the field shows the time", () => {
    const picked = new Date(2024, 5, 1, 9, 5);
    expect(formatDateAnswer(picked, true)).toBe(picked.toISOString());
    expect(parseDateAnswer(picked.toISOString(), true)!.getTime()).toBe(picked.getTime());
  });

  it("has no date for an empty or unparseable answer", () => {
    expect(parseDateAnswer(null)).toBeNull();
    expect(parseDateAnswer("")).toBeNull();
    expect(parseDateAnswer("not a date")).toBeNull();
  });
});
