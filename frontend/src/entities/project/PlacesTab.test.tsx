import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import type { DetailTabHelpers } from "../types";
import { PlacesTab } from "./PlacesTab";
import type { ProjectDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
});

function project(overrides: Partial<ProjectDetail> = {}): ProjectDetail {
  return {
    resourceType: "projects",
    id: "5",
    name: "Chantier",
    ...overrides,
  } as ProjectDetail;
}

function render(entity: ProjectDetail, helpers: Partial<DetailTabHelpers> = {}) {
  const fullHelpers: DetailTabHelpers = { refetch: vi.fn(), ...helpers };
  act(() => {
    root.render(<PlacesTab entity={entity} helpers={fullHelpers} />);
  });
  return fullHelpers;
}

describe("PlacesTab", () => {
  it("renders every place from spatialContext", () => {
    render(
      project({
        spatialContext: [
          { resourceType: "places", id: "1", name: "Cave A" },
          { resourceType: "places", id: "2", name: "Cave B" },
        ],
      }),
    );

    expect(container.textContent).toContain("Cave A");
    expect(container.textContent).toContain("Cave B");
  });

  it("flags the mainLocation row as the principal one", () => {
    render(
      project({
        mainLocation: { resourceType: "places", id: "1", name: "Cave A" },
        spatialContext: [
          { resourceType: "places", id: "1", name: "Cave A" },
          { resourceType: "places", id: "2", name: "Cave B" },
        ],
      }),
    );

    expect(container.textContent).toContain("Localisation principale");
  });

  it("adds mainLocation as its own row when spatialContext doesn't already carry it", () => {
    render(
      project({
        mainLocation: { resourceType: "places", id: "9", name: "Cave principale" },
        spatialContext: [{ resourceType: "places", id: "2", name: "Cave B" }],
      }),
    );

    expect(container.textContent).toContain("Cave principale");
    expect(container.textContent).toContain("Cave B");
  });

  it("shows an empty message when there is no spatial context at all", () => {
    render(project());

    expect(container.textContent).toContain("Aucun lieu");
  });

  it("opens the place's overview on row click, via onOpenOverview", () => {
    const onOpenOverview = vi.fn();
    render(
      project({ spatialContext: [{ resourceType: "places", id: "1", name: "Cave A" }] }),
      { onOpenOverview },
    );

    act(() => {
      container.querySelector(".entity-list-panel-identifier-link")!.dispatchEvent(
        new MouseEvent("click", { bubbles: true }),
      );
    });

    expect(onOpenOverview).toHaveBeenCalledWith("place", "1");
  });

  it("falls back to onNavigate when there is no onOpenOverview", () => {
    const onNavigate = vi.fn();
    render(
      project({ spatialContext: [{ resourceType: "places", id: "1", name: "Cave A" }] }),
      { onNavigate },
    );

    act(() => {
      container.querySelector(".entity-list-panel-identifier-link")!.dispatchEvent(
        new MouseEvent("click", { bubbles: true }),
      );
    });

    expect(onNavigate).toHaveBeenCalledWith("place", "1");
  });
});
