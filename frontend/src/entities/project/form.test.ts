import { describe, expect, it } from "vitest";
import { panelLabel, parseLayout, toPrimeFlexClass } from "./form";

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
              { width: { span: 12, md: 6, lg: 3 }, hidden: false, isRequired: false, isReadOnly: false, fieldId: -102 },
              { width: { span: 12, md: 6, lg: 3 }, hidden: true, isRequired: true, isReadOnly: true, fieldId: -103 },
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
      width: { span: 12, md: 6, lg: 3 },
      hidden: true,
      isRequired: true,
      isReadOnly: true,
      fieldId: -103,
    });
  });
});

describe("toPrimeFlexClass", () => {
  it("emits a class per breakpoint that was set", () => {
    // md/lg are container-query classes of our own (sia-md-col-*/sia-lg-col-*), not PrimeFlex's
    // viewport-media-query md:col-*/lg:col-* — see the function's own doc for why: those never
    // react to the overview pane's splitter narrowing while the window itself stays wide.
    expect(toPrimeFlexClass({ span: 12, md: 6, lg: 3 })).toBe("col-12 sia-md-col-6 sia-lg-col-3");
  });

  it("omits a breakpoint that was left unset", () => {
    expect(toPrimeFlexClass({ span: 12 })).toBe("col-12");
  });

  it("can override just one breakpoint", () => {
    expect(toPrimeFlexClass({ span: 12, lg: 4 })).toBe("col-12 sia-lg-col-4");
  });

  it("falls back to a full-width column instead of throwing when width is missing", () => {
    // EffectiveFormResolver's own "additional fields" column reached this with no width at all —
    // a network-boundary value the type doesn't protect against at runtime.
    expect(toPrimeFlexClass(null)).toBe("col-12");
    expect(toPrimeFlexClass(undefined)).toBe("col-12");
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
