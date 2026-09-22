import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { renderAnswerCell, renderAnswerValue } from "./display";
import type { FieldResource } from "./types";

function field(overrides: Partial<FieldResource> = {}): FieldResource {
  return {
    id: "-120",
    resourceType: "fields",
    label: "Périodes",
    answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE",
    isSystemField: false,
    ...overrides,
  };
}

function ref(id: string, label: string) {
  return { resourceId: id, resourceType: "concepts", label };
}

function markup(node: React.ReactNode): string {
  return renderToStaticMarkup(<>{node}</>);
}

describe("renderAnswerValue (plain text)", () => {
  it("joins every label, so it stays usable as a tooltip / for tests", () => {
    const text = renderAnswerValue(field(), [ref("1", "Néolithique"), ref("2", "Âge du Fer")]);
    expect(text).toBe("Néolithique, Âge du Fer");
  });
});

describe("renderAnswerCell (one-line cell)", () => {
  it("shows the first label plus a +N counter for a multi-valued answer", () => {
    const html = markup(renderAnswerCell(field(), [ref("1", "Néolithique"), ref("2", "Âge du Fer"), ref("3", "Gallo-romain")]));
    expect(html).toContain("Néolithique");
    expect(html).toContain("+2");
    // Explicitly NOT the joined list — that is what would overflow the single line.
    expect(html).not.toContain("Âge du Fer</span>");
  });

  it("keeps the full list on the counter's tooltip", () => {
    const html = markup(renderAnswerCell(field(), [ref("1", "Néolithique"), ref("2", "Âge du Fer")]));
    expect(html).toContain('title="Néolithique, Âge du Fer"');
  });

  it("renders a single-valued array as the bare label, with no counter", () => {
    const html = markup(renderAnswerCell(field(), [ref("1", "Néolithique")]));
    expect(html).toBe("Néolithique");
  });

  it("ignores empty labels when counting", () => {
    const html = markup(renderAnswerCell(field(), [ref("1", "Néolithique"), null, undefined]));
    expect(html).toBe("Néolithique");
  });

  it("renders an empty array and a null answer as nothing", () => {
    expect(markup(renderAnswerCell(field(), []))).toBe("");
    expect(markup(renderAnswerCell(field(), null))).toBe("");
  });

  it("renders a scalar answer as its plain value", () => {
    expect(markup(renderAnswerCell(field({ answerType: "TEXT" }), "OA-2024"))).toBe("OA-2024");
  });

  it("unwraps a FieldAnswer envelope the same way the text version does", () => {
    const html = markup(
      renderAnswerCell(field(), { answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE", values: [ref("1", "A"), ref("2", "B")] }),
    );
    expect(html).toContain("+1");
  });
});
