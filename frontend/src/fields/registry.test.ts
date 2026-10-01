import { describe, expect, it, vi } from "vitest";
import { getFieldRenderer, registerFallbackFieldRenderer, registerFieldRenderer } from "./registry";

// The registry module holds module-level Maps, so each test registers only the answerTypes it
// needs under a unique name to avoid cross-test interference (there's no reset function, by
// design — registration is meant to be a one-time startup step, see registerDefaultRenderers.ts).
describe("field renderer registry", () => {
  it("returns the renderer registered for an answerType", () => {
    const renderer = vi.fn();
    registerFieldRenderer("TEST_TYPE_A", renderer);
    expect(getFieldRenderer("TEST_TYPE_A")).toBe(renderer);
  });

  it("falls back to the configured fallback renderer for an unregistered answerType", () => {
    const fallback = vi.fn();
    registerFallbackFieldRenderer(fallback);
    expect(getFieldRenderer("TEST_TYPE_UNKNOWN_B")).toBe(fallback);
  });
});

describe("field renderer registry without a fallback configured", () => {
  it("throws for an unregistered answerType when no fallback was ever set", async () => {
    vi.resetModules();
    const fresh = await import("./registry");
    expect(() => fresh.getFieldRenderer("TEST_TYPE_UNKNOWN_C")).toThrow(/no fallback/i);
  });
});
