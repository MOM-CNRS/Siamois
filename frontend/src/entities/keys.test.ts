import { describe, expect, it } from "vitest";
import { ENTITY_KEYS, isEntityKey } from "./keys";

describe("isEntityKey", () => {
  it("accepts every registry key of the application", () => {
    ENTITY_KEYS.forEach((key) => expect(isEntityKey(key)).toBe(true));
  });

  it("rejects anything else, including a missing value", () => {
    expect(isEntityKey("recording-units")).toBe(false);
    expect(isEntityKey("")).toBe(false);
    expect(isEntityKey(undefined)).toBe(false);
  });
});
