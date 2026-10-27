import {describe, expect, it} from "vitest";
import {renderToStaticMarkup} from "react-dom/server";
import {BooleanRenderer, FallbackRenderer, MeasurementRenderer, TextRenderer} from "./renderers";
import type {FieldResource} from "./types";

function field(overrides: Partial<FieldResource> = {}): FieldResource {
  return {
    id: "1",
    resourceType: "fields",
    label: "Type",
    answerType: "SELECT_ONE",
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

describe("TextRenderer", () => {
  function renderText(over: Partial<FieldResource>): string {
    return renderToStaticMarkup(
      <TextRenderer
        field={field({ answerType: "TEXT", ...over })}
        value="Un commentaire"
        readOnly={false}
        required={false}
        onChange={() => {}}
      />,
    );
  }

  it("uses a textarea for a field flagged isTextArea (CustomFieldText.isTextArea)", () => {
    expect(renderText({ isTextArea: true })).toContain("<textarea");
  });

  it("uses a single-line input otherwise, including when the flag is absent", () => {
    expect(renderText({ isTextArea: false })).toContain("<input");
    expect(renderText({})).toContain("<input");
  });
});

describe("BooleanRenderer", () => {
  function renderBoolean(value: unknown, constraints?: FieldResource["constraints"]): string {
    return renderToStaticMarkup(
      <BooleanRenderer
        field={field({ answerType: "BOOLEAN", constraints })}
        value={value}
        readOnly={false}
        required={false}
        onChange={() => {}}
      />,
    );
  }

  it("is a single checkbox when the field does not allow 'no answer'", () => {
    const html = renderBoolean(true);
    expect(html).toContain("p-checkbox");
    expect(html).not.toContain("Non spécifié");
  });

  it("is a tri-state checkbox (yes / no / not specified) when the field allows 'no answer'", () => {
    const html = renderBoolean(null, { allowNoAnswer: true });
    expect(html).toContain("boolean-renderer-tristate");
    expect(html).toContain("Non renseigné");
  });
});

describe("MeasurementRenderer", () => {
  function renderMeasurement(constraints: FieldResource["constraints"], value: unknown = null): string {
    return renderToStaticMarkup(
      <MeasurementRenderer
        field={field({ answerType: "MEASUREMENT", constraints })}
        value={value}
        readOnly={false}
        required={false}
        onChange={() => {}}
      />,
    );
  }

  it("shows a single unit in a disabled selector", () => {
    const html = renderMeasurement({ unit: "m²" });
    expect(html).toContain("p-dropdown");
    expect(html).toMatch(/<div class="[^"]*p-dropdown[^"]*"[^>]*data-p-disabled="true"/);
    expect(html).toContain("m²");
  });

  it("lets the user pick among several units, starting from the one the answer was entered in", () => {
    const html = renderMeasurement({ unit: "m²", units: ["m²", "ha"] }, { numericValue: 3, symbol: "ha" });
    expect(html).toContain("p-dropdown");
    expect(html).toContain("ha");
    expect(html).not.toMatch(/<div class="[^"]*p-dropdown[^"]*"[^>]*data-p-disabled="true"/);
  });
});
