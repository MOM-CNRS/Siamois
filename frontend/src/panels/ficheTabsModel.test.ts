import { describe, expect, it } from "vitest";
import { registerEntityType } from "../entities/registry";
import type { DetailTabDef, EntityTypeConfig } from "../entities/types";
import { editableTabs, hasVisibleTab, moveTab, newListTab, relationTargets, removeTab, restorableTabs, restoreTab, shownTabs, toConfigTabs } from "./ficheTabsModel";

registerEntityType({
  key: "tabs-model-target",
  labels: { singular: "Cible", plural: "Cibles" },
  collectionPath: "targets",
  icon: "bi bi-x",
  api: { list: () => Promise.resolve({ data: [], totalCount: 0, limit: 50, offset: 0 }), get: () => Promise.resolve({ id: "1" }) },
  list: { columns: [], searchable: false },
  detail: { tabs: [] },
  routes: { list: "/tabs-model-target", detail: (id: string | number) => `/tabs-model-target/${id}` },
} as unknown as EntityTypeConfig<unknown, unknown>);

const code = [
  { key: "fiche", label: "Fiche", render: () => null },
  { key: "targets", label: "Cibles", relation: { target: "tabs-model-target" }, render: () => null },
  { key: "gone-target", label: "Disparue", relation: { target: "removed" }, render: () => null },
] as DetailTabDef<never>[];

describe("editableTabs", () => {
  it("is the code's tabs when nothing is configured", () => {
    expect(editableTabs(code, undefined).map((r) => [r.key, r.label, r.defaultLabel, r.hidden])).toEqual([
      ["fiche", "", "Fiche", false],
      ["targets", "", "Cibles", false],
      ["gone-target", "", "Disparue", false],
    ]);
  });

  it("follows the configuration, keeps hidden tabs, appends what it doesn't name, drops what is gone", () => {
    const rows = editableTabs(code, {
      tabs: [
        { key: "targets", label: "Mes cibles", hidden: true },
        { key: "old" },
        { key: "custom:1", kind: "list", target: "tabs-model-target", label: "Filtrée", baseFilters: { f: { op: "in", v: ["1"] } } },
        { key: "custom:2", kind: "list", target: "removed", label: "Perdue" },
      ],
    });
    expect(rows.map((r) => `${r.key}:${r.label}:${r.hidden}`)).toEqual([
      "targets:Mes cibles:true",
      "custom:1:Filtrée:false",
      "fiche::false",
      "gone-target::false",
    ]);
    expect(rows[1].baseFilters).toEqual({ f: { op: "in", v: ["1"] } });
  });
});

describe("toConfigTabs", () => {
  it("writes every tab in order, only what differs from the code, and reads back the same", () => {
    const rows = editableTabs(code, {
      tabs: [
        { key: "targets", label: "  Mes cibles ", hidden: true },
        { key: "custom:1", kind: "list", target: "tabs-model-target", label: "Filtrée", baseFilters: { f: { op: "in", v: ["1"] } } },
      ],
    });
    const saved = toConfigTabs(rows);
    expect(saved).toEqual({
      v: 1,
      tabs: [
        { key: "targets", label: "Mes cibles", hidden: true },
        { key: "custom:1", label: "Filtrée", kind: "list", target: "tabs-model-target", baseFilters: { f: { op: "in", v: ["1"] } } },
        { key: "fiche" },
        { key: "gone-target" },
      ],
    });
    expect(editableTabs(code, saved)).toEqual(rows.map((r) => (r.label === "Mes cibles" ? { ...r } : r)));
  });
});

describe("the editor's helpers", () => {
  it("lists the targets the fiche may add, only those that exist", () => {
    expect(relationTargets(code)).toEqual(["tabs-model-target"]);
  });

  it("names a new list tab after its type when left unnamed, with a key of its own", () => {
    const a = newListTab("tabs-model-target", "  ", {});
    const b = newListTab("tabs-model-target", "Mes cibles", { f: { op: "contains", v: "x" } });
    expect(a.label).toBe("Cibles");
    expect(b.label).toBe("Mes cibles");
    expect(a.key).not.toBe(b.key);
    expect(b.key.startsWith("custom:")).toBe(true);
    expect(b.baseFilters).toEqual({ f: { op: "contains", v: "x" } });
  });

  it("moves a line, and stays put at the ends", () => {
    const rows = editableTabs(code, undefined);
    expect(moveTab(rows, "fiche", 1).map((r) => r.key)).toEqual(["targets", "fiche", "gone-target"]);
    expect(moveTab(rows, "fiche", -1)).toBe(rows);
    expect(moveTab(rows, "gone-target", 1)).toBe(rows);
  });

  it("wants one tab shown", () => {
    const rows = editableTabs(code, undefined);
    expect(hasVisibleTab(rows)).toBe(true);
    expect(hasVisibleTab(rows.map((r) => ({ ...r, hidden: true })))).toBe(false);
  });
});

describe("removing and restoring tabs", () => {
  it("only removes lists: a built-in relation tab is hidden (and can come back), a project's list is dropped, the fiche's own tabs stay", () => {
    let rows = editableTabs(code, { tabs: [{ key: "custom:1", kind: "list", target: "tabs-model-target", label: "Filtrée" }] });
    expect(rows.map((r) => [r.key, r.removable])).toEqual([
      ["custom:1", true],
      ["fiche", false],
      ["targets", true],
      ["gone-target", true],
    ]);

    rows = removeTab(rows, "fiche");
    expect(shownTabs(rows).map((r) => r.key)).toContain("fiche");

    rows = removeTab(rows, "targets");
    expect(shownTabs(rows).map((r) => r.key)).not.toContain("targets");
    expect(restorableTabs(rows).map((r) => r.key)).toEqual(["targets"]);

    rows = removeTab(rows, "custom:1");
    expect(rows.map((r) => r.key)).not.toContain("custom:1");

    rows = restoreTab(rows, "targets");
    expect(shownTabs(rows).map((r) => r.key)).toEqual(["fiche", "gone-target", "targets"]);
    expect(restorableTabs(rows)).toEqual([]);
  });

  it("moves among the tabs shown, skipping those removed", () => {
    let rows = editableTabs(code, undefined);
    rows = removeTab(rows, "targets");
    expect(moveTab(rows, "fiche", 1).filter((r) => !r.hidden).map((r) => r.key)).toEqual(["gone-target", "fiche"]);
  });

  it("shows a tab that cannot be removed even when an older configuration hid it", () => {
    const rows = editableTabs(code, { tabs: [{ key: "fiche", hidden: true }] });
    expect(rows.find((r) => r.key === "fiche")!.hidden).toBe(false);
  });
});
