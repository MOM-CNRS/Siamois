import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch } from "../api/client";
import { getEffectiveForm, loadTypeCatalog } from "./typeCatalog";

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

describe("getEffectiveForm", () => {
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

    const result = await getEffectiveForm("recording-unit-types", "5", "88");

    expect(result.layoutJson).toBe('[{"name":"typed"}]');
    expect(result.fields).toEqual({ "2": { id: "2" } });
  });

  it("falls back to _default when the RU has no type", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [],
      _default: { formBundle: { resourceType: "forms", layoutJson: '[{"name":"default"}]' }, tableColumns: [], fields: { "9": { id: "9" } } },
      fields: {},
    });

    const result = await getEffectiveForm("recording-unit-types", "5", null);

    expect(result.layoutJson).toBe('[{"name":"default"}]');
    expect(result.fields).toEqual({ "9": { id: "9" } });
  });

  it("falls back to _default when the RU's type id matches no configured type", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [{ id: "77", formBundle: { resourceType: "forms", layoutJson: "[]" }, fields: {} }],
      _default: { formBundle: { resourceType: "forms", layoutJson: '[{"name":"default"}]' }, tableColumns: [], fields: {} },
      fields: {},
    });

    const result = await getEffectiveForm("recording-unit-types", "5", "unknown-type-id");

    expect(result.layoutJson).toBe('[{"name":"default"}]');
  });

  it("returns an empty layout and fields when the matched entry has no formBundle", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      data: [],
      _default: { formBundle: null, tableColumns: [] },
      fields: {},
    });

    const result = await getEffectiveForm("recording-unit-types", "5", null);

    expect(result.layoutJson).toBe("");
    expect(result.fields).toEqual({});
  });
});

describe("fetchTypesCatalog", () => {
  it("fetches a catalog once for every reader of the same path", async () => {
    mockedApiFetch.mockResolvedValue({ data: [], _default: { formBundle: { layoutJson: "[]" }, fields: {} } });

    await getEffectiveForm("phase-types", "5", null);
    await getEffectiveForm("phase-types", "5", "9");
    await loadTypeCatalog({ scope: { entityType: "project", id: "5" } }, "phase-types");

    expect(mockedApiFetch).toHaveBeenCalledTimes(1);
    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5/phase-types");
  });
});
