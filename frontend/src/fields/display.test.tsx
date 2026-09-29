import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { entityChipStyle, entityColor, renderAnswerCell, renderAnswerValue } from "./display";
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

  it("renders a single-valued array as one chip, with no counter", () => {
    const html = markup(renderAnswerCell(field(), [ref("1", "Néolithique")]));
    expect(html).toContain(">Néolithique</span>");
    expect(html).not.toContain("cell-multi-more");
  });

  it("ignores empty labels when counting", () => {
    const html = markup(renderAnswerCell(field(), [ref("1", "Néolithique"), null, undefined]));
    expect(html).toContain(">Néolithique</span>");
    expect(html).not.toContain("cell-multi-more");
  });

  it("shows every value when asked to (a fiche field, which wraps)", () => {
    const html = markup(renderAnswerCell(field(), [ref("1", "Néolithique"), ref("2", "Âge du Fer")], { all: true }));
    expect(html).toContain(">Néolithique</span>");
    expect(html).toContain(">Âge du Fer</span>");
    expect(html).not.toContain("cell-multi-more");
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

// A reference reads as a light chip in its type's colour — the edit-mode token without its remove
// button — and opens its fiche in the overview when it has one.
describe("reference chips", () => {
  it("colours a chip by what it points at", () => {
    const ru = markup(renderAnswerCell(field({ answerType: "SELECT_MULTIPLE_RECORDING_UNIT" }), [
      { resourceId: "4", resourceType: "recording-units", label: "US-4" },
    ]));
    expect(ru).toContain('class="ref-chip"');
    expect(ru).toContain("--ground-main-color");

    const concept = markup(renderAnswerCell(field(), [ref("1", "Néolithique")]));
    expect(concept).toContain("--siamois-green");
  });

  it("accepts a flat resolved resource (ProjectResource.type / mainLocation)", () => {
    const html = markup(renderAnswerCell(field({ answerType: "SELECT_ONE_SPATIAL_UNIT" }), { id: 9, resourceType: "spatial-units", name: "Bibracte" }));
    expect(html).toContain(">Bibracte</span>");
    expect(html).toContain("--context-main-color");
  });

  it("is plain text only for a scalar", () => {
    expect(markup(renderAnswerCell(field({ answerType: "TEXT" }), "OA-2024"))).not.toContain("ref-chip");
  });
});

// An entity chip takes its entity's colour, never the colour of the panel it sits in (a recording
// unit listed in a project's tab is still red).
describe("entity colours", () => {
  it("maps each entity type to the theme's colour for it", () => {
    expect(entityColor("recordingUnit")).toContain("--ground-main-color");
    expect(entityColor("find")).toContain("--ground-main-color");
    expect(entityColor("phase")).toContain("--ground-main-color");
    expect(entityColor("project")).toContain("--context-main-color");
    expect(entityColor("place")).toContain("--context-main-color");
    expect(entityColor("container")).toContain("--third-main-color");
  });

  it("gives a chip its colour through --entity-chip-color, and nothing for an unknown type", () => {
    expect(entityChipStyle("recordingUnit")).toEqual({ "--entity-chip-color": entityColor("recordingUnit") });
    expect(entityChipStyle("unknown")).toBeUndefined();
    expect(entityChipStyle(undefined)).toBeUndefined();
  });
});

describe("a multi-valued preview (MultiValue, complete=false)", () => {
  const preview = {
    values: [ref("1", "US 1")],
    total: 37,
    complete: false,
    _links: { values: "/api/v1/recording-units/42/fields/-319/values" },
  };

  it("counts what it leaves out from the total, not from the preview", () => {
    const html = markup(renderAnswerCell(field(), preview));
    expect(html).toContain("US 1");
    expect(html).toContain("+36");
    // The counter opens the whole list rather than pretending to hold it.
    expect(html).toContain("cell-multi-more-link");
  });

  it("says so in plain text too", () => {
    expect(renderAnswerValue(field(), preview)).toBe("US 1 (+36)");
  });

  it("shows a complete MultiValue like a plain list", () => {
    const html = markup(renderAnswerCell(field(), { values: [ref("1", "A"), ref("2", "B")], total: 2, complete: true }));
    expect(html).toContain("+1");
    expect(html).not.toContain("cell-multi-more-link");
  });

  it("shows nothing for an empty one", () => {
    expect(markup(renderAnswerCell(field(), { values: [], total: 0, complete: true }))).toBe("");
  });
});

describe("a stratigraphic relationship", () => {
  it("reads as its concept, the other unit, and a ? when uncertain", () => {
    const relation = {
      resourceId: "12",
      resourceType: "recording-units",
      label: "US 12",
      qualifier: { concept: { label: "coupe" }, position: "posterior", uncertain: true },
    };
    expect(renderAnswerValue(field({ answerType: "SELECT_MULTIPLE_STRATIGRAPHY" }), [relation]))
      .toBe("coupe US 12 ?");
  });
});
