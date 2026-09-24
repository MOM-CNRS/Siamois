import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../api/client";
import {
  fetchConceptOptions,
  fetchPlaceOptions,
  filterKindForAnswerType,
  optionSourceFor,
  referenceTargetOf,
  supportsEmptyQuery,
} from "./optionSources";
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

describe("optionSourceFor — reference fields", () => {
  it("searches the organization's members for a person field, labelled by full name", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [{ id: "3", username: "jdoe", name: "Jane", lastname: "Doe" }] });

    const options = await optionSourceFor(field({ answerType: "SELECT_MULTIPLE_PERSON" }), 100)!("doe");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/users?organizationId=100&limit=20&search=doe");
    expect(options).toEqual([{ id: "3", label: "Jane Doe" }]);
  });

  it("searches the edited entity's project for a phase field", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [{ id: 8, identifier: "P1", title: "Phase 1" }] });

    const options = await optionSourceFor(field({ answerType: "SELECT_MULTIPLE_PHASE" }), 100, "5")!("ph");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5/phases?offset=0&limit=20&search=ph");
    expect(options).toEqual([{ id: "8", label: "Phase 1" }]);
  });

  it("falls back to the organization-wide list without a project (finds live at /mobiliers under a project, /finds at the top)", async () => {
    mockedApiFetch.mockResolvedValue({ data: [{ id: 2, fullIdentifier: "M-2" }] });

    await optionSourceFor(field({ answerType: "SELECT_MULTIPLE_SPECIMEN" }), 100)!();
    expect(mockedApiFetch).toHaveBeenLastCalledWith("/api/v1/finds?offset=0&limit=20&organizationId=100");

    await optionSourceFor(field({ answerType: "SELECT_MULTIPLE_SPECIMEN" }), 100, "5")!();
    expect(mockedApiFetch).toHaveBeenLastCalledWith("/api/v1/projects/5/mobiliers?offset=0&limit=20");
  });

  it("asks a legacy vocabulary field's own suggestions by field id, in the project", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [{ id: "11", resolvedLabel: "Argile" }] });

    const options = await optionSourceFor(field({ id: "42", answerType: "SELECT_ONE", fieldCode: null }), 100, "5")!("ar");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/100/concepts?fieldId=42&projectId=5&q=ar");
    expect(options).toEqual([{ id: "11", label: "Argile" }]);
  });

  it("has no source for the read-only kinds", () => {
    expect(optionSourceFor(field({ answerType: "SELECT_ONE_ACTION_CODE" }), 100)).toBeNull();
    expect(optionSourceFor(field({ answerType: "SELECT_ADDRESS" }), 100)).toBeNull();
  });
});

describe("referenceTargetOf", () => {
  it.each([
    ["SELECT_ONE_SPATIAL_UNIT", "spatial-units", "place"],
    ["SELECT_MULTIPLE_RECORDING_UNIT", "recording-units", "recordingUnit"],
    ["SELECT_MULTIPLE_SPECIMEN", "finds", "find"],
    ["SELECT_MULTIPLE_CONTAINER", "containers", "container"],
    ["SELECT_MULTIPLE_PHASE", "phases", "phase"],
    ["SELECT_ONE_PERSON", "persons", undefined],
    ["SELECT_ONE_ACTION_UNIT", "action-units", undefined],
    ["SELECT_ONE_FROM_FIELD_CODE", "concepts", undefined],
  ])("%s references %s, created as %s", (answerType, resourceType, createEntityType) => {
    expect(referenceTargetOf(field({ answerType }))).toEqual(
      createEntityType ? { resourceType, createEntityType } : { resourceType },
    );
  });
});
