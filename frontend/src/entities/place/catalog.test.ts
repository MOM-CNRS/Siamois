import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../../api/client";
import { loadPlaceCatalog } from "./catalog";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

const field = (id: string, label: string) => ({ id, resourceType: "fields", label, answerType: "TEXT", isSystemField: true });

const CATALOG = {
  fields: {
    "-201": field("-201", "Catégorie"),
    "-202": field("-202", "Nom"),
    "-203": field("-203", "Code"),
    "-205": field("-205", "N° de regroupement"),
  },
  data: [],
};

beforeEach(() => {
  mockedApiFetch.mockReset();
  mockedApiFetch.mockResolvedValue(CATALOG);
});

describe("loadPlaceCatalog", () => {
  it("reads the organization's catalog", async () => {
    await loadPlaceCatalog({ organizationId: 7 });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/7/place-types");
  });

  it("reads the organization's catalog for the contained places of a place, whose scope is not a project", async () => {
    await loadPlaceCatalog({ organizationId: 7, scope: { entityType: "place", id: 3 } });

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/7/place-types");
  });

  it("keeps the name out of the picker (it is the pinned column) but its field available", async () => {
    const catalog = await loadPlaceCatalog({ organizationId: 7 });

    expect(catalog.columns.map((c) => c.fieldId)).toEqual(["-201", "-203", "-205"]);
    expect(Object.keys(catalog.fields).sort()).toEqual(["-201", "-202", "-203", "-205"]);
  });

  it("shows the type and the place number by default, as the list did before its picker", async () => {
    const catalog = await loadPlaceCatalog({ organizationId: 7 });

    expect(catalog.columns.map((c) => [c.fieldId, c.visible])).toEqual([
      ["-201", true],
      ["-203", false],
      ["-205", true],
    ]);
    expect(catalog.columns.map((c) => c.order)).toEqual([0, 1, 2]);
  });
});
