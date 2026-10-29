import {describe, expect, it} from "vitest";
import type {FormLayoutPanel} from "./layout";
import {onTheFlySegment, withOnTheFlyFields} from "./onTheFly";
import type {FieldResource} from "./types";

const panel = (name: string): FormLayoutPanel => ({ name, rows: [] });
const measure = { id: "9", answerType: "MEASUREMENT" } as unknown as FieldResource;

describe("withOnTheFlyFields", () => {
  it("adds the record's fields to the panel they were added to, without touching the shared layout", () => {
    const panels = [panel("a"), panel("b")];
    const result = withOnTheFlyFields(panels, {}, { fields: { "9": measure }, placements: [{ fieldId: "9", panel: "a" }] });

    expect(result.panels[0].rows[0].columns[0].fieldId).toBe("9");
    // a quarter of the row, like every other field's default width
    expect(result.panels[0].rows[0].columns[0].width).toEqual({ span: 12, md: 6, lg: 3 });
    expect(result.panels[1]).toBe(panels[1]);
    expect(panels[0].rows).toHaveLength(0);
    expect(result.fields["9"]).toBe(measure);
    expect(result.onTheFlyIds.has("9")).toBe(true);
  });

  it("falls back to the last panel for a field without one, or whose panel is gone", () => {
    const result = withOnTheFlyFields([panel("a"), panel("b")], {}, {
      fields: { "9": measure, "10": measure },
      placements: [{ fieldId: "9" }, { fieldId: "10", panel: "gone" }],
    });

    expect(result.panels[0].rows).toHaveLength(0);
    expect(result.panels[1].rows[0].columns.map((c) => c.fieldId)).toEqual(["9", "10"]);
  });

  it("returns the form as it is when the record has no fields of its own", () => {
    const panels = [panel("a")];
    expect(withOnTheFlyFields(panels, {}, undefined).panels).toBe(panels);
  });
});

describe("onTheFlySegment", () => {
  it("knows the six kinds of record, and not a place", () => {
    expect(onTheFlySegment("recordingUnit")).toBe("recording-units");
    expect(onTheFlySegment("study")).toBe("studies");
    expect(onTheFlySegment("place")).toBeUndefined();
  });
});
