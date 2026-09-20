import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { FallbackRenderer } from "./renderers";
import type { FieldResource } from "./types";

function field(overrides: Partial<FieldResource> = {}): FieldResource {
  return {
    id: "1",
    resourceType: "fields",
    label: "Type",
    answerType: "SELECT_ONE_FROM_FIELD_CODE",
    isSystemField: true,
    ...overrides,
  };
}

function render(value: unknown): string {
  return renderToStaticMarkup(
    <FallbackRenderer field={field()} value={value} readOnly required={false} onChange={() => {}} />,
  );
}

describe("FallbackRenderer", () => {
  it("shows an em-dash for null/undefined", () => {
    expect(render(null)).toContain("—");
    expect(render(undefined)).toContain("—");
  });

  it("shows the plain value for a scalar", () => {
    expect(render(42)).toContain("42");
  });

  it("resolves a resource object to its resolvedLabel", () => {
    expect(render({ resourceType: "concepts", id: "9", resolvedLabel: "Fouille" })).toContain("Fouille");
  });

  it("resolves a resource object with only a name to that name", () => {
    expect(render({ resourceType: "places", id: "1", name: "Lyon" })).toContain("Lyon");
  });

  it("joins an array of resource objects into a comma-separated label list", () => {
    const value = [
      { resourceType: "places", id: "1", name: "Lyon" },
      { resourceType: "places", id: "2", name: "Paris" },
    ];
    expect(render(value)).toContain("Lyon, Paris");
  });

  it("falls back to an em-dash for an object with no recognizable label", () => {
    expect(render({ resourceType: "places", id: "1" })).toContain("—");
  });
});
