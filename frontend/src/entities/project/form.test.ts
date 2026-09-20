import { describe, expect, it } from "vitest";
import { panelLabel, parseLayout } from "./form";

describe("parseLayout", () => {
  it("returns an empty array for an empty layoutJson", () => {
    expect(parseLayout("")).toEqual([]);
  });

  it("parses panels/rows/columns exactly as FormUiDtoLayoutJson.serialize emits them", () => {
    const layoutJson = JSON.stringify([
      {
        className: null,
        name: "common.header.general",
        canUserAddFields: null,
        isSystemPanel: true,
        rows: [
          {
            columns: [
              { className: "ui-g-12 ui-md-6 ui-lg-3", isRequired: false, isReadOnly: false, fieldId: -102 },
              { className: "d-none", isRequired: true, isReadOnly: true, fieldId: -103 },
            ],
          },
        ],
      },
    ]);

    const panels = parseLayout(layoutJson);
    expect(panels).toHaveLength(1);
    expect(panels[0].name).toBe("common.header.general");
    expect(panels[0].rows[0].columns).toHaveLength(2);
    expect(panels[0].rows[0].columns[1]).toEqual({
      className: "d-none",
      isRequired: true,
      isReadOnly: true,
      fieldId: -103,
    });
  });
});

describe("panelLabel", () => {
  it("translates a known ActionUnitDetailsForm panel code", () => {
    expect(panelLabel("actionunit.header.administrative")).toBe("Administratif");
  });

  it("falls back to the raw code for an unknown panel", () => {
    expect(panelLabel("some.unknown.code")).toBe("some.unknown.code");
  });
});
