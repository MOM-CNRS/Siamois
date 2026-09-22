import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../api/client";
import { fetchConceptOptions, fetchPlaceOptions, filterKindForAnswerType, optionSourceFor, supportsEmptyQuery } from "./optionSources";
import type { FieldResource } from "./types";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockClear();
});

function field(overrides: Partial<FieldResource>): FieldResource {
  return {
    id: "-118",
    resourceType: "fields",
    label: "Statut",
    answerType: "SELECT_ONE_FROM_FIELD_CODE",
    isSystemField: true,
    ...overrides,
  };
}

describe("filterKindForAnswerType", () => {
  it.each([
    ["TEXT", "contains"],
    ["DECIMAL", "range"],
    ["SELECT_ONE_FROM_FIELD_CODE", "concept-one"],
    ["SELECT_MULTIPLE_FROM_FIELD_CODE", "concept-many"],
    ["SELECT_ONE_SPATIAL_UNIT", "spatial-one"],
  ] as const)("maps %s to %s", (answerType, expected) => {
    expect(filterKindForAnswerType(answerType)).toBe(expected);
  });

  it("returns null for an answerType with no filter widget (e.g. INTEGER, MEASUREMENT)", () => {
    expect(filterKindForAnswerType("INTEGER")).toBeNull();
    expect(filterKindForAnswerType("MEASUREMENT")).toBeNull();
  });
});

describe("fetchConceptOptions", () => {
  it("calls the org-scoped concepts endpoint and maps resolvedLabel to label", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ id: "7", resolvedLabel: "En cours" }, { id: "8", resolvedLabel: null, externalUrl: "1234" }],
    });

    const options = await fetchConceptOptions(100, "SIARU.STATUS", "cour");

    expect(mockedApiFetch).toHaveBeenCalledWith(
      expect.stringMatching(/^\/api\/v1\/organizations\/100\/concepts\?/),
    );
    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("fieldCode=SIARU.STATUS");
    expect(path).toContain("q=cour");
    expect(options).toEqual([
      { id: "7", label: "En cours" },
      { id: "8", label: "1234" },
    ]);
  });

  it("omits q when absent", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [] });
    await fetchConceptOptions(100, "SIARU.STATUS");
    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).not.toContain("q=");
  });
});

describe("fetchPlaceOptions", () => {
  it("calls the places autocomplete endpoint and maps name to label", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [{ id: 42, name: "Lyon" }] });

    const options = await fetchPlaceOptions(100, "lyo");

    const [path] = mockedApiFetch.mock.calls[0];
    expect(path).toContain("/api/v1/places/autocomplete?");
    expect(path).toContain("organizationId=100");
    expect(path).toContain("q=lyo");
    expect(options).toEqual([{ id: "42", label: "Lyon" }]);
  });
});

describe("optionSourceFor", () => {
  it("returns a concept loader for SELECT_ONE_FROM_FIELD_CODE with a fieldCode", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [] });
    const loader = optionSourceFor(field({ fieldCode: "SIARU.STATUS" }), 100);
    expect(loader).not.toBeNull();
    await loader?.("q");
    expect(mockedApiFetch.mock.calls[0][0]).toContain("fieldCode=SIARU.STATUS");
  });

  it("returns null for SELECT_ONE_FROM_FIELD_CODE without a fieldCode", () => {
    expect(optionSourceFor(field({ fieldCode: null }), 100)).toBeNull();
  });

  it("returns a place loader for SELECT_ONE_SPATIAL_UNIT", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [] });
    const loader = optionSourceFor(field({ answerType: "SELECT_ONE_SPATIAL_UNIT" }), 100);
    expect(loader).not.toBeNull();
    await loader?.("lyo");
    expect(mockedApiFetch.mock.calls[0][0]).toContain("/places/autocomplete?");
  });

  it("short-circuits an empty spatial-unit query instead of firing a request the API rejects", async () => {
    const loader = optionSourceFor(field({ answerType: "SELECT_ONE_SPATIAL_UNIT" }), 100);
    expect(await loader?.("")).toEqual([]);
    expect(await loader?.("   ")).toEqual([]);
    expect(await loader?.(undefined)).toEqual([]);
    // PlaceSearchControllerApi#autocomplete answers 400 on a blank q — no call must be made.
    expect(mockedApiFetch).not.toHaveBeenCalled();
  });

  it("lets an empty concept query through — the endpoint returns the whole vocabulary for it", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [] });
    const loader = optionSourceFor(field({ fieldCode: "SIARU.STATUS" }), 100);
    await loader?.("");
    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
    expect(mockedApiFetch.mock.calls[0][0]).not.toContain("q=");
  });

  it("returns the same place loader for the spatial-unit TREE field (SPATIAL_CONTEXT)", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [] });
    const loader = optionSourceFor(field({ answerType: "SELECT_MULTIPLE_SPATIAL_UNIT_TREE" }), 100);
    expect(loader).not.toBeNull();
    await loader?.("lyo");
    expect(mockedApiFetch.mock.calls[0][0]).toContain("/places/autocomplete?");
    // Same 400-on-blank rule as the single picker.
    expect(await loader?.("")).toEqual([]);
    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
  });

  it("returns null for an answerType with no option source (e.g. TEXT, DECIMAL)", () => {
    expect(optionSourceFor(field({ answerType: "TEXT" }), 100)).toBeNull();
    expect(optionSourceFor(field({ answerType: "DECIMAL" }), 100)).toBeNull();
  });
});

describe("supportsEmptyQuery", () => {
  it("is true for concept-backed fields and false for spatial units", () => {
    expect(supportsEmptyQuery(field({ answerType: "SELECT_ONE_FROM_FIELD_CODE" }))).toBe(true);
    expect(supportsEmptyQuery(field({ answerType: "SELECT_MULTIPLE_FROM_FIELD_CODE" }))).toBe(true);
    expect(supportsEmptyQuery(field({ answerType: "SELECT_ONE_SPATIAL_UNIT" }))).toBe(false);
    expect(supportsEmptyQuery(field({ answerType: "SELECT_MULTIPLE_SPATIAL_UNIT_TREE" }))).toBe(false);
  });
});
