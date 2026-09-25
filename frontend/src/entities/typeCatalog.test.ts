import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../api/client";
import { loadTypeCatalog } from "./typeCatalog";

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

beforeEach(() => mockedApiFetch.mockReset());

const field = (id: string, label: string) => ({
  id,
  resourceType: "fields",
  label,
  answerType: "TEXT",
  isSystemField: id.startsWith("-"),
});

describe("loadTypeCatalog", () => {
  it("offers every field of every type as a hidden column, additional ones included", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      _default: { fields: { "-503": field("-503", "Titre") } },
      data: [{ fields: { "-503": field("-503", "Titre"), "12": field("12", "Note") } }],
    });

    const catalog = await loadTypeCatalog({ scope: { entityType: "project", id: 5 } }, "phase-types");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5/phase-types");
    expect(Object.keys(catalog.fields).sort()).toEqual(["-503", "12"]);
    expect(catalog.columns).toEqual([
      { fieldId: "-503", columnId: "-503", visible: false, order: 0 },
      { fieldId: "12", columnId: "12", visible: false, order: 1 },
    ]);
  });

  it("reads the organization's aggregate catalog for an organization-wide list", async () => {
    mockedApiFetch.mockResolvedValueOnce({ _default: { fields: { "12": field("12", "Note") } }, data: [] });

    const catalog = await loadTypeCatalog({ organizationId: 7 }, "phase-types");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/7/phase-types");
    expect(Object.keys(catalog.fields)).toEqual(["12"]);
  });

  it("has nothing to offer for a scoped list without a project, nor without any context", async () => {
    expect(await loadTypeCatalog({ organizationId: 7, scope: { entityType: "place", id: 3 } }, "phase-types"))
      .toEqual({ fields: {}, columns: [] });
    expect(await loadTypeCatalog({}, "phase-types")).toEqual({ fields: {}, columns: [] });
    expect(mockedApiFetch).not.toHaveBeenCalled();
  });
});
