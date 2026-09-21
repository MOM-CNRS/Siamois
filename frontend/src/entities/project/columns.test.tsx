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
  it("is exactly the three pinned, non-form columns", () => {
    expect(projectColumns.map((c) => c.key)).toEqual(["fullIdentifier", "name", "recordingUnitCount"]);
  });

  it("falls back to identifier when fullIdentifier is blank", () => {
    const row = project({ fullIdentifier: "", identifier: "FA" });
    expect(renderCell("fullIdentifier", row)).toBe("FA");
  });

  it("renders the project name", () => {
    const row = project({ name: "Fouille B" });
    expect(renderCell("name", row)).toBe("Fouille B");
  });

  it("defaults the recording-unit count to 0 when _counts is absent", () => {
    const row = project({ _counts: undefined });
    expect(renderCell("recordingUnitCount", row)).toBe("0");
  });

  it("renders the actual recording-unit count when present", () => {
    const row = project({ _counts: { children: 0, recordingUnits: 7 } });
    expect(renderCell("recordingUnitCount", row)).toBe("7");
  });

  it("marks every pinned column as sortable, matching ALLOWED_PROJECT_SORT_FIELDS", () => {
    expect(findColumn("fullIdentifier").sortable).toBe(true);
    expect(findColumn("name").sortable).toBe(true);
    expect(findColumn("recordingUnitCount").sortable).toBe(true);
  });
});
