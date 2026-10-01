import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { getProjectTypes } from "./projectTypes";

vi.mock("../../api/client", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => {
  mockedApiFetch.mockClear();
});

describe("getProjectTypes", () => {
  it("fetches the organization's project types and flattens _default into layoutJson/fieldConfigs/fields", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [],
      _default: {
        form: { resourceType: "forms", layoutJson: "[]" },
        fieldConfigs: [{ field: "-102", active: true, institutionLocked: true }],
        tableColumns: [{ columnId: "status", fieldId: "-118", visible: true, order: 0 }],
      },
      fields: {
        "-102": {
          id: "-102",
          resourceType: "fields",
          label: "Nom",
          answerType: "TEXT",
          isSystemField: true,
          valueBinding: "name",
        },
      },
    });

    const result = await getProjectTypes(7);

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/7/project-types");
    expect(result).toEqual({
      layoutJson: "[]",
      fieldConfigs: [{ field: "-102", active: true, institutionLocked: true }],
      tableColumns: [{ columnId: "status", fieldId: "-118", visible: true, order: 0 }],
      fields: {
        "-102": {
          id: "-102",
          resourceType: "fields",
          label: "Nom",
          answerType: "TEXT",
          isSystemField: true,
          valueBinding: "name",
        },
      },
    });
  });
});
