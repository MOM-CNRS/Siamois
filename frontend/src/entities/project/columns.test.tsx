import {describe, expect, it} from "vitest";
import {renderToStaticMarkup} from "react-dom/server";
import {projectColumns} from "./columns";
import type {ProjectSummary} from "./types";

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
  it("is exactly the one pinned, non-form column", () => {
    expect(projectColumns.map((c) => c.key)).toEqual(["name"]);
  });

  it("makes the name column the identifier (chip) column", () => {
    expect(findColumn("name").identifier).toBe(true);
  });

  it("renders the project name", () => {
    const row = project({ name: "Fouille B" });
    expect(renderCell("name", row)).toBe("Fouille B");
  });

  it("marks the pinned name column as sortable", () => {
    expect(findColumn("name").sortable).toBe(true);
  });
});
