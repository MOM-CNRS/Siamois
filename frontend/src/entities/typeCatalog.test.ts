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

    const catalog = await loadTypeCatalog({ entityType: "project", id: 5 }, "phase-types");

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/projects/5/phase-types");
    expect(Object.keys(catalog.fields).sort()).toEqual(["-503", "12"]);
    expect(catalog.columns).toEqual([
      { fieldId: "-503", columnId: "-503", visible: false, order: 0 },
      { fieldId: "12", columnId: "12", visible: false, order: 1 },
    ]);
  });

  it("has nothing to offer without a project in scope", async () => {
    expect(await loadTypeCatalog(undefined, "phase-types")).toEqual({ fields: {}, columns: [] });
    expect(mockedApiFetch).not.toHaveBeenCalled();
  });
});
