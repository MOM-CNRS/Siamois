import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { patchProject } from "./api";
import { ProjectDetailHeader } from "./DetailHeader";
import { getProjectTypes } from "./projectTypes";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import type { ProjectDetail } from "./types";


vi.mock("./api", () => ({ patchProject: vi.fn() }));
vi.mock("./projectTypes", () => ({ getProjectTypes: vi.fn() }));
const mockedPatchProject = vi.mocked(patchProject);
const mockedGetProjectTypes = vi.mocked(getProjectTypes);

// ActionUnitForm.ACTION_UNIT_TYPE_FIELD as the org catalog serves it — the category chip finds it
// by valueBinding "type", never by its id.
const typeField: FieldResource = {
  id: "-101",
  resourceType: "fields",
  label: "Catégorie",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "type",
  fieldCode: "SIAAU.TYPE",
};

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

function renderHeader(entity: ProjectDetail, onSaved = vi.fn(), writeMode = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        {/* The pencils follow headerEditControls.xhtml's gate: the app's read/write switch AND the
            user's own right. These cases are about the controls themselves, so write mode is on;
            the read-mode case has its own test below. */}
        <WriteModeProvider value={writeMode}>
          <ProjectDetailHeader entity={entity} onSaved={onSaved} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  return onSaved;
}

beforeEach(() => {
  mockedPatchProject.mockReset();
  mockedGetProjectTypes.mockReset();
  mockedGetProjectTypes.mockResolvedValue({
    layoutJson: "[]",
    fieldConfigs: [],
    tableColumns: [],
    fields: { "-101": typeField },
  });
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

function headerPencil() {
  return container.querySelector(".project-detail-header-edit") as HTMLElement;
}

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

    await act(async () => {
      headerPencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const input = container.querySelector(".project-detail-header-identifier-input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "   ");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      headerPencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mockedPatchProject).not.toHaveBeenCalled();
    expect(container.textContent).toContain("L'identifiant est obligatoire");
  });

  it("saves every changed field with ONE validate button and one patch", async () => {
    mockedPatchProject.mockResolvedValue({} as never);
    const onSaved = renderHeader(project());
    await flush();

    await act(async () => {
      headerPencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.querySelectorAll(".pi-check")).toHaveLength(1);
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    for (const [selector, value] of [
      [".project-detail-header-name-input", "Fouille B"],
      [".project-detail-header-identifier-input", "FB"],
    ] as const) {
      const input = container.querySelector(selector) as HTMLInputElement;
      await act(async () => {
        nativeSetter.call(input, value);
        input.dispatchEvent(new Event("input", { bubbles: true }));
      });
    }
    await act(async () => {
      headerPencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mockedPatchProject).toHaveBeenCalledTimes(1);
    expect(mockedPatchProject).toHaveBeenCalledWith("1", { name: "Fouille B", identifier: "FB" });
    expect(onSaved).toHaveBeenCalled();
  });

  it("has a single pencil, which turns name, identifier and type into editors", async () => {
    renderHeader(project());
    await flush();

    expect(container.querySelectorAll(".pi-pencil")).toHaveLength(1);
    await act(async () => {
      headerPencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.querySelector(".project-detail-header-name-input")).toBeTruthy();
    expect(container.querySelector(".project-detail-header-identifier-input")).toBeTruthy();
    expect(container.querySelector(".project-detail-header-category .p-autocomplete input")).toBeTruthy();
  });
});

describe("ProjectDetailHeader — category chip", () => {
  it("shows the type as a chip", async () => {
    renderHeader(project());
    await flush();

    expect(container.querySelector(".project-detail-header-category")?.textContent).toContain("Sondage");
  });

  it("labels an untyped project rather than rendering an empty chip", async () => {
    renderHeader(project({ type: undefined }));
    await flush();

    expect(container.querySelector(".project-detail-header-category")?.textContent).toContain("Sans type");
  });

  it("keeps the type as a chip when the catalog has no type field to edit with", async () => {
    mockedGetProjectTypes.mockResolvedValue({ layoutJson: "[]", fieldConfigs: [], tableColumns: [], fields: {} });
    renderHeader(project());
    await flush();
    await act(async () => {
      headerPencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(container.querySelector(".project-detail-header-category")?.textContent).toContain("Sondage");
    expect(container.querySelector(".project-detail-header-category .p-autocomplete")).toBeFalsy();
  });

  it("swaps the chip for the shared concept autocomplete in edit mode", async () => {
    renderHeader(project());
    await flush();

    await act(async () => {
      headerPencil().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const input = container.querySelector(".project-detail-header-category .p-autocomplete input") as HTMLInputElement;
    expect(input).toBeTruthy();
    expect(input.value).toBe("Sondage");
  });
});

describe("ProjectDetailHeader — the app's read/write switch gates both chips", () => {
  it("offers no pencil at all in read mode, on either chip", async () => {
    renderHeader(project(), vi.fn(), false);
    await flush();

    expect(container.querySelector(".pi-pencil")).toBeNull();
    // The values themselves stay on screen — read mode hides the controls, not the data.
    expect(container.textContent).toContain("INST-FA-2024");
    expect(container.textContent).toContain("Sondage");
  });

  it("offers no pencil in write mode when the user has no edit right on this project", async () => {
    renderHeader(project({ _permissions: { canEdit: false, canDelete: false } }));
    await flush();

    expect(container.querySelector(".pi-pencil")).toBeNull();
  });
});
