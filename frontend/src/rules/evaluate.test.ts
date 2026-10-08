import { describe, expect, it } from "vitest";
import { columnsDependencies, ruleDependencies } from "./dependencies";
import { evaluateForm } from "./evaluate";
import { filterOptionsOffline, offlineIsAllowed } from "./offline";
import type { RuledColumn } from "./types";

describe("evaluateForm", () => {
  it("disables a field whose condition throws rather than crashing the form", () => {
    const columns = [{ fieldId: 1, rules: { enabledWhen: { fieldId: 2, op: "BOGUS" as never } } }];
    expect(evaluateForm(columns, () => "x").get("1")?.enabled).toBe(false);
  });

  it("skips columns without a fieldId", () => {
    expect([...evaluateForm([{ fieldId: null }, { fieldId: 3 }], () => undefined).keys()]).toEqual(["3"]);
  });

  it("keeps the tightest bound among several constraints", () => {
    const columns: RuledColumn[] = [
      { fieldId: 1 },
      { fieldId: 2 },
      { fieldId: 3, rules: { constraints: [{ op: "GTE", fieldId: 1 }, { op: "GT", fieldId: 2 }] } },
    ];
    const values: Record<string, unknown> = { "1": 5, "2": 7 };
    const min = evaluateForm(columns, (id) => values[id]).get("3")?.bounds?.min;
    expect(min).toMatchObject({ value: 7, exclusive: true, fieldId: "2" });
  });

  it("flags a value outside the offline related concepts", () => {
    const columns: RuledColumn[] = [
      { fieldId: 1 },
      { fieldId: 2, rules: { options: { kind: "RELATED_CONCEPTS", fieldId: 1 } } },
    ];
    const values: Record<string, unknown> = { "1": { resourceId: "10" }, "2": { resourceId: "99" } };
    const states = evaluateForm(columns, (id) => values[id], { isAllowed: offlineIsAllowed({ "10": ["20", "21"] }) });
    expect(states.get("2")?.incoherent.map((r) => r.kind)).toEqual(["OUT_OF_OPTIONS"]);
  });
});

describe("filterOptionsOffline", () => {
  const options = [{ id: "20" }, { id: "21" }, { id: "30" }];

  it("keeps only the concepts related to the parent value", () => {
    const ctx = { kind: "RELATED_CONCEPTS" as const, parentFieldId: "1", relatedTo: "10" };
    expect(filterOptionsOffline(options, ctx, { "10": ["21", "30"] })).toEqual([{ id: "21" }, { id: "30" }]);
  });

  it("offers nothing while the parent is empty", () => {
    const ctx = { kind: "RELATED_CONCEPTS" as const, parentFieldId: "1", relatedTo: null };
    expect(filterOptionsOffline(options, ctx, {})).toEqual([]);
  });

  it("leaves an unfiltered field alone", () => {
    expect(filterOptionsOffline(options, undefined, {})).toBe(options);
  });
});

describe("dependencies", () => {
  it("collects every field a rule set reads", () => {
    expect([
      ...ruleDependencies({
        enabledWhen: { all: [{ fieldId: 1, op: "EMPTY" }, { not: { fieldId: 2, op: "EMPTY" } }] },
        requiredWhen: { fieldId: 3, op: "EMPTY" },
        options: { kind: "RELATED_CONCEPTS", fieldId: 4 },
        constraints: [{ op: "GTE", fieldId: 5 }],
      }),
    ]).toEqual(["1", "2", "3", "4", "5"]);
  });

  it("adds what visible columns need, including the declaring side of a constraint", () => {
    const columns: RuledColumn[] = [
      { fieldId: 1 },
      { fieldId: 2, rules: { enabledWhen: { fieldId: 1, op: "NOT_EMPTY" } } },
      { fieldId: 3, rules: { constraints: [{ op: "GTE", fieldId: 4 }] } },
      { fieldId: 4 },
    ];
    expect([...columnsDependencies(["2", "4"], columns)].sort()).toEqual(["1", "3"]);
  });
});

describe("placeContextOf", () => {
  const geoplat = {
    source: "GEOPLAT",
    params: { citycode: { fromField: -108, attribute: "CODE" as const } },
  };

  it("has no context for a field with no place source", () => {
    expect(evaluateForm([{ fieldId: -104 }], () => undefined).get("-104")?.placeContext).toBeUndefined();
  });

  it("names the place picked in the field a source parameter reads", () => {
    const columns: RuledColumn[] = [{ fieldId: -108 }, { fieldId: -104, rules: { placeSources: [{ source: "INSEE" }, geoplat] } }];
    const values: Record<string, unknown> = { "-108": { resourceId: "7", resourceType: "spatial-units", label: "Lyon" } };

    expect(evaluateForm(columns, (id) => values[id]).get("-104")?.placeContext).toEqual({ deps: { "-108": "7" } });
  });

  it("is null for a field that is still empty, and a source reading nothing still gets a context", () => {
    const columns: RuledColumn[] = [
      { fieldId: -108, rules: { placeSources: [{ source: "INSEE" }] } },
      { fieldId: -104, rules: { placeSources: [geoplat] } },
    ];
    const states = evaluateForm(columns, () => undefined);

    expect(states.get("-108")?.placeContext).toEqual({ deps: {} });
    expect(states.get("-104")?.placeContext).toEqual({ deps: { "-108": null } });
  });

  it("makes the places a source parameter reads a dependency of the field", () => {
    expect([...ruleDependencies({ placeSources: [geoplat] })]).toEqual(["-108"]);
  });
});
