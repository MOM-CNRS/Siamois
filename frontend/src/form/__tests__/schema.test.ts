import { describe, expect, it } from "vitest";
import { parseLayoutJson } from "../schema";

describe("parseLayoutJson", () => {
  it("parses a real layout array (bare array, not wrapped in a 'layout' key)", () => {
    const layoutJson = JSON.stringify([
      {
        className: "panel",
        name: "recordingunit.details",
        isSystemPanel: true,
        rows: [{ columns: [{ fieldId: 7, className: "ui-g-12 ui-md-6 ui-lg-3", isRequired: true }] }],
      },
    ]);
    const layout = parseLayoutJson(layoutJson);
    expect(layout).toHaveLength(1);
    expect(layout[0].rows[0].columns[0].fieldId).toBe(7);
    expect(layout[0].rows[0].columns[0].isRequired).toBe(true);
  });

  it("treats the server's empty-layout sentinel '[]' as an empty layout", () => {
    expect(parseLayoutJson("[]")).toEqual([]);
  });

  it("treats null/undefined/empty string as an empty layout", () => {
    expect(parseLayoutJson(null)).toEqual([]);
    expect(parseLayoutJson(undefined)).toEqual([]);
    expect(parseLayoutJson("")).toEqual([]);
  });

  it("treats a non-array JSON value (e.g. a stray legacy '{}') as an empty layout rather than throwing", () => {
    expect(parseLayoutJson("{}")).toEqual([]);
    expect(parseLayoutJson('{"layout":[]}')).toEqual([]);
  });

  it("treats malformed JSON as an empty layout rather than throwing", () => {
    expect(parseLayoutJson("not json")).toEqual([]);
  });
});
