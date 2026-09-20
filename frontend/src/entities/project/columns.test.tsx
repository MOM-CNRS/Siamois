import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { projectColumns } from "./columns";
import type { ProjectSummary } from "./types";

function project(overrides: Partial<ProjectSummary>): ProjectSummary {
  return {
    resourceType: "projects",
    id: "1",
    name: "Fouille A",
    fullIdentifier: "INST-FA-2024",
    identifier: "FA",
    ...overrides,
  };
}

function findColumn(key: string) {
  const column = projectColumns.find((c) => c.key === key);
  if (!column) throw new Error(`no column "${key}"`);
  return column;
}

function renderCell(key: string, row: ProjectSummary): string {
  return renderToStaticMarkup(<>{findColumn(key).render(row)}</>);
}

describe("projectColumns", () => {
  it("falls back to identifier when fullIdentifier is blank", () => {
    const row = project({ fullIdentifier: "", identifier: "FA" });
    expect(renderCell("fullIdentifier", row)).toBe("FA");
  });

  it("renders the resolved type label when present", () => {
    const row = project({ type: { resourceType: "concepts", id: "9", resolvedLabel: "Fouille" } });
    expect(renderCell("type", row)).toBe("Fouille");
  });

  it("renders an empty string for an absent type", () => {
    const row = project({ type: null });
    expect(renderCell("type", row)).toBe("");
  });

  it("truncates a date to its ISO date prefix", () => {
    const row = project({ beginDate: "2024-05-01T00:00:00Z" });
    expect(renderCell("beginDate", row)).toBe("2024-05-01");
  });

  it("defaults the recording-unit count to 0 when _counts is absent", () => {
    const row = project({ _counts: undefined });
    expect(renderCell("recordingUnits", row)).toBe("0");
  });

  it("marks only the API-sortable columns as sortable", () => {
    expect(findColumn("fullIdentifier").sortable).toBe(true);
    expect(findColumn("name").sortable).toBe(true);
    expect(findColumn("type").sortable).toBeFalsy();
    expect(findColumn("mainLocation").sortable).toBeFalsy();
  });
});
