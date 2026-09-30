import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { getRecordingUnitTypes } from "./recordingUnitTypes";

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
