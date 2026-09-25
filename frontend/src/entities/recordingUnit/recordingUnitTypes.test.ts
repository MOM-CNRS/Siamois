import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { getRecordingUnitEffectiveForm, getRecordingUnitTypes } from "./recordingUnitTypes";

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockClear();
});

describe("getRecordingUnitTypes", () => {
  it("fetches the project-scoped catalog, not an organization-scoped one", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [],
      _default: { formBundle: null, tableColumns: [] },
      fields: {},
    });

    await getRecordingUnitTypes(5);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5/recording-unit-types");
  });

  it("returns _default.tableColumns and the root fields map", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [],
      _default: {
        formBundle: null,
        tableColumns: [{ columnId: "type", fieldId: "-80", visible: true, order: 0 }],
      },
      fields: { "-80": { id: "-80", resourceType: "fields", label: "Type", answerType: "SELECT_ONE_FROM_FIELD_CODE", isSystemField: true } },
    });

    const result = await getRecordingUnitTypes("5");

    expect(result.tableColumns).toEqual([{ columnId: "type", fieldId: "-80", visible: true, order: 0 }]);
    expect(result.fields).toHaveProperty("-80");
  });

  it("defaults tableColumns to an empty array when absent", async () => {
    mockedApiFetch.mockResolvedValueOnce({ data: [], _default: { formBundle: null }, fields: {} });

    const result = await getRecordingUnitTypes(5);

    expect(result.tableColumns).toEqual([]);
  });
});

describe("getRecordingUnitEffectiveForm", () => {
  it("resolves the per-type entry matching the RU's own type id", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [
        { id: "77", formBundle: { resourceType: "forms", layoutJson: "[]" }, fields: { "1": { id: "1" } } },
        {
          id: "88",
          formBundle: { resourceType: "forms", layoutJson: '[{"name":"typed"}]' },
          fields: { "2": { id: "2" } },
        },
      ],
      _default: { formBundle: { resourceType: "forms", layoutJson: '[{"name":"default"}]' }, tableColumns: [], fields: {} },
      fields: {},
    });

    const result = await getRecordingUnitEffectiveForm("5", "88");

    expect(result.layoutJson).toBe('[{"name":"typed"}]');
    expect(result.fields).toEqual({ "2": { id: "2" } });
  });

  it("falls back to _default when the RU has no type", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [],
      _default: { formBundle: { resourceType: "forms", layoutJson: '[{"name":"default"}]' }, tableColumns: [], fields: { "9": { id: "9" } } },
      fields: {},
    });

    const result = await getRecordingUnitEffectiveForm("5", null);

    expect(result.layoutJson).toBe('[{"name":"default"}]');
    expect(result.fields).toEqual({ "9": { id: "9" } });
  });

  it("falls back to _default when the RU's type id matches no configured type", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ id: "77", formBundle: { resourceType: "forms", layoutJson: "[]" }, fields: {} }],
      _default: { formBundle: { resourceType: "forms", layoutJson: '[{"name":"default"}]' }, tableColumns: [], fields: {} },
      fields: {},
    });

    const result = await getRecordingUnitEffectiveForm("5", "unknown-type-id");

    expect(result.layoutJson).toBe('[{"name":"default"}]');
  });

  it("returns an empty layout and fields when the matched entry has no formBundle", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [],
      _default: { formBundle: null, tableColumns: [] },
      fields: {},
    });

    const result = await getRecordingUnitEffectiveForm("5", null);

    expect(result.layoutJson).toBe("");
    expect(result.fields).toEqual({});
  });
});
