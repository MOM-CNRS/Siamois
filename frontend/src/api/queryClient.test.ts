import { describe, expect, it } from "vitest";
import { ApiError } from "./client";
import { shouldRetry } from "./queryClient";

describe("shouldRetry", () => {
  it("never retries an answer that won't change (4xx)", () => {
    [400, 403, 404, 409].forEach((status) => expect(shouldRetry(0, new ApiError(status, "no"))).toBe(false));
  });

  it("retries a server error or a network failure at most twice", () => {
    expect(shouldRetry(0, new ApiError(500, "boom"))).toBe(true);
    expect(shouldRetry(1, new TypeError("Failed to fetch"))).toBe(true);
    expect(shouldRetry(2, new ApiError(503, "down"))).toBe(false);
  });
});
