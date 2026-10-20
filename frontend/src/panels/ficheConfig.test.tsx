import { describe, expect, it } from "vitest";
import { ficheScopeOf, parseFicheTabs, resolveTabs } from "./ficheConfig";
import { registerEntityType } from "../entities/registry";
import type { DetailTabDef, EntityTypeConfig } from "../entities/types";

interface Fake {
  id: string;
}

registerEntityType({
  key: "fiche-config-target",
  labels: { singular: "Cible", plural: "Cibles" },
  collectionPath: "targets",
  icon: "bi bi-x",
  api: { list: () => Promise.resolve({ data: [], totalCount: 0, limit: 50, offset: 0 }), get: () => Promise.resolve({ id: "1" }) },
  list: { columns: [], searchable: false },
  detail: { tabs: [] },
  routes: { list: "/fiche-config-target", detail: (id: string | number) => `/fiche-config-target/${id}` },
} as unknown as EntityTypeConfig<unknown, unknown>);

const code: DetailTabDef<Fake>[] = [
  { key: "fiche", label: "Fiche", render: () => null },
  { key: "docs", label: "Documents", render: () => null },
  { key: "ru", label: "UE", render: () => null },
];

const keys = (tabs: DetailTabDef<Fake>[]) => tabs.map((t) => `${t.key}:${t.label}`);

describe("parseFicheTabs", () => {
  it("keeps what is valid, in order, each key once", () => {
    const parsed = parseFicheTabs({
      v: 1,
      tabs: [
        { key: "a", label: "  Alpha  " },
        null,
        "x",
        { label: "no key" },
        { key: "a", label: "again" },
        { key: "b", hidden: true },
        { key: "c", kind: "list", target: "t", baseFilters: { f: { op: "in", v: ["1"] }, bad: { op: "nope" } } },
      ],
    });
    expect(parsed).toEqual([
      { key: "a", label: "Alpha" },
      { key: "b", hidden: true },
      { key: "c", kind: "list", target: "t", baseFilters: { f: { op: "in", v: ["1"] } } },
    ]);
  });

  it("reads anything else as no configuration", () => {
    expect(parseFicheTabs(undefined)).toEqual([]);
    expect(parseFicheTabs({ tabs: "nope" })).toEqual([]);
  });
});

describe("resolveTabs", () => {
  it("is the code's tabs without configuration", () => {
    expect(resolveTabs(code, undefined, "project")).toBe(code);
  });

  it("orders, renames and hides; a tab the configuration doesn't name comes last", () => {
    const tabs = resolveTabs(code, { tabs: [{ key: "ru", label: "Unités" }, { key: "fiche", hidden: true }] }, "project");
    expect(keys(tabs)).toEqual(["ru:Unités", "docs:Documents"]);
  });

  it("skips a name the code no longer has and falls back on the code when everything is hidden", () => {
    expect(keys(resolveTabs(code, { tabs: [{ key: "old" }, { key: "docs" }] }, "project"))).toEqual([
      "docs:Documents",
      "fiche:Fiche",
      "ru:UE",
    ]);
    const hidden = { tabs: code.map((t) => ({ key: t.key, hidden: true })) };
    expect(resolveTabs(code, hidden, "project")).toBe(code);
  });

  it("adds a list tab of a known target, and skips one whose target is gone", () => {
    const tabs = resolveTabs(
      code,
      {
        tabs: [
          { key: "fiche" },
          { key: "custom:1", kind: "list", target: "fiche-config-target", label: "Cibles du projet" },
          { key: "custom:2", kind: "list", target: "removed-type", label: "Gone" },
        ],
      },
      "project",
    );
    expect(keys(tabs)).toEqual(["fiche:Fiche", "custom:1:Cibles du projet", "docs:Documents", "ru:UE"]);
  });
});

describe("ficheScopeOf", () => {
  it("is the project itself for a project, the fiche's project otherwise, and the sub-type from its type", () => {
    expect(ficheScopeOf("project", { id: "5", type: { id: "9" } })).toEqual({ projectId: 5, typeId: 9 });
    expect(ficheScopeOf("recordingUnit", { id: "7", projectId: "5", type: { id: "21" } })).toEqual({ projectId: 5, typeId: 21 });
    expect(ficheScopeOf("place", { id: "7" })).toEqual({ projectId: undefined, typeId: undefined });
  });
});
