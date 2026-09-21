import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import { ProjectFicheTab } from "./FicheTab";
import { getProjectTypes } from "./projectTypes";
import { getProjectHistory } from "./history";
import { patchProject } from "./api";
import type { ProjectDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./projectTypes", () => ({ getProjectTypes: vi.fn() }));
vi.mock("./history", () => ({ getProjectHistory: vi.fn() }));
vi.mock("./api", () => ({ patchProject: vi.fn() }));

const mockedGetProjectTypes = vi.mocked(getProjectTypes);
const mockedGetProjectHistory = vi.mocked(getProjectHistory);
const mockedPatchProject = vi.mocked(patchProject);

registerDefaultFieldRenderers();

// Layout mirrors the real ActionUnit.DETAILS_FORM shape (FormUiDtoLayoutJson.serialize) closely
// enough to exercise every branch FicheTab/FormField actually take: a backed+editable field
// (name), a hidden one (identifier, "d-none" — edited via the header instead), a backed field
// with no renderer yet (type, falls back to its resolvedLabel), and an unbacked one (oaCode —
// real ActionUnitDetailsForm field, no equivalent on ProjectResource at all).
const layout = [
  {
    className: null,
    name: "common.header.general",
    isSystemPanel: true,
    canUserAddFields: null,
    rows: [
      {
        columns: [
          { className: "col-name", isRequired: false, isReadOnly: false, fieldId: -102 },
          { className: "d-none", isRequired: true, isReadOnly: true, fieldId: -103 },
          { className: "col-type", isRequired: false, isReadOnly: false, fieldId: -101 },
          { className: "col-oacode", isRequired: false, isReadOnly: false, fieldId: 42 },
        ],
      },
    ],
  },
];

const fields = {
  "-102": { id: "-102", resourceType: "fields", label: "Nom", answerType: "TEXT", isSystemField: true, valueBinding: "name" },
  "-103": { id: "-103", resourceType: "fields", label: "Identifiant", answerType: "TEXT", isSystemField: true, valueBinding: "identifier" },
  "-101": {
    id: "-101",
    resourceType: "fields",
    label: "Type",
    answerType: "SELECT_ONE_FROM_FIELD_CODE",
    isSystemField: true,
    valueBinding: "type",
  },
  "42": { id: "42", resourceType: "fields", label: "Code OA", answerType: "TEXT", isSystemField: true, valueBinding: "oaCode" },
};

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

function renderFiche(entity: ProjectDetail, onSaved = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <ProjectFicheTab entity={entity} onSaved={onSaved} />
      </QueryClientProvider>,
    );
  });
  return onSaved;
}

beforeEach(() => {
  mockedGetProjectTypes.mockReset();
  mockedGetProjectHistory.mockReset();
  mockedPatchProject.mockReset();
  mockedGetProjectTypes.mockResolvedValue({ layoutJson: JSON.stringify(layout), fieldConfigs: [], tableColumns: [], fields });
  mockedGetProjectHistory.mockResolvedValue([]);
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

describe("ProjectFicheTab", () => {
  it("renders the schema-driven layout: editable field, hidden identifier column, unbacked field as unavailable", async () => {
    renderFiche(project());
    await flush();

    expect(container.textContent).toContain("Nom");
    const nameInput = container.querySelector(".col-name input") as HTMLInputElement;
    expect(nameInput.value).toBe("Fouille A");

    // identifier column is "d-none" in the layout — not rendered in the grid at all, only in
    // the header's own identifier control.
    expect(container.textContent).not.toContain("Identifiant");

    // type now has a real renderer (SelectOneConceptRenderer) — it shows as a disabled
    // AutoComplete input, so its value lives in the input's `value` attribute, not in
    // container.textContent (unlike the old FallbackRenderer's plain <span>).
    const typeInput = container.querySelector(".col-type input") as HTMLInputElement;
    expect(typeInput).toBeTruthy();
    expect(typeInput.value).toBe("Sondage");
    expect(typeInput.disabled).toBe(true);

    // oaCode has no equivalent on ProjectResource at all.
    expect(container.textContent).toContain("Code OA");
    expect(container.textContent).toContain("Non disponible");
  });

  it("shows the most recent revision's date and author when history has entries", async () => {
    mockedGetProjectHistory.mockResolvedValue([
      { revisionNumber: 2, revisionDate: "2026-09-01T10:00:00Z", revisionType: "MOD", author: { id: 1, name: "Ada", lastname: "Lovelace" } },
    ]);
    renderFiche(project());
    await flush();

    expect(container.textContent).toContain("Dernière modification");
    expect(container.textContent).toContain("Ada Lovelace");
  });

  it("saves name/beginDate/endDate via patchProject and calls onSaved", async () => {
    mockedPatchProject.mockResolvedValue(project({ name: "Renamed" }));
    const onSaved = renderFiche(project());
    await flush();

    const editButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Modifier")!;
    await act(async () => {
      editButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    const nameInput = container.querySelector(".col-name input") as HTMLInputElement;
    expect(nameInput).toBeTruthy();
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(nameInput, "Renamed");
      nameInput.dispatchEvent(new Event("input", { bubbles: true }));
    });

    const saveButton = Array.from(container.querySelectorAll("button")).find((b) => b.textContent === "Enregistrer")!;
    await act(async () => {
      saveButton.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mockedPatchProject).toHaveBeenCalledWith("1", { name: "Renamed", beginDate: undefined, endDate: undefined });
    expect(onSaved).toHaveBeenCalled();
  });
});
