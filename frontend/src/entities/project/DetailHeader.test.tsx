import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { patchProject } from "./api";
import { ProjectDetailHeader } from "./DetailHeader";
import type { ProjectDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./api", () => ({ patchProject: vi.fn() }));
const mockedPatchProject = vi.mocked(patchProject);

// actionUnitPanelHeader.xhtml's content — split out of FicheTab.test.tsx once this moved into
// EntityDetailPanel's own panel header (plan §7/§8 follow-up: "the toolbar is part of the panel
// header"), rendered by entities/project/config.tsx's detail.header rather than by the fiche tab.

function project(overrides: Partial<ProjectDetail> = {}): ProjectDetail {
  return {
    resourceType: "projects",
    id: "1",
    name: "Fouille A",
    fullIdentifier: "INST-FA-2024",
    identifier: "FA",
    organization: { resourceType: "organizations", id: "7" },
    type: { resourceType: "concepts", id: "9", resolvedLabel: "Sondage" },
    ...overrides,
  };
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

function renderHeader(entity: ProjectDetail, onSaved = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <ProjectDetailHeader entity={entity} onSaved={onSaved} />
      </QueryClientProvider>,
    );
  });
  return onSaved;
}

beforeEach(() => {
  mockedPatchProject.mockReset();
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

describe("ProjectDetailHeader", () => {
  it("shows the identifier (fullIdentifier, falling back to identifier)", async () => {
    renderHeader(project({ fullIdentifier: "", identifier: "FA" }));
    await flush();
    expect(container.textContent).toContain("FA");
  });

  it("shows the type/name/location chips (actionUnitPanelHeader.xhtml)", async () => {
    renderHeader(project({ mainLocation: { resourceType: "places", id: "3", name: "Lyon" } }));
    await flush();

    expect(container.querySelector(".action-unit-type-chip")).toBeTruthy();
    expect(container.textContent).toContain("Sondage");
    expect(container.textContent).toContain("Fouille A");
    expect(container.textContent).toContain("Lyon");
  });

  it("rejects a blank identifier without calling patchProject", async () => {
    renderHeader(project());
    await flush();

    const pencil = container.querySelector(".project-fiche-tab-identifier button") as HTMLElement;
    await act(async () => {
      pencil.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const input = container.querySelector(".project-fiche-tab-identifier input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "   ");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });

    const checkButton = container.querySelector(".project-fiche-tab-identifier .pi-check")!.closest("button") as HTMLElement;
    await act(async () => {
      checkButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mockedPatchProject).not.toHaveBeenCalled();
    expect(container.textContent).toContain("L'identifiant est obligatoire");
  });
});
