import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import { ContainerFicheTab } from "./FicheTab";
import { getContainerEffectiveForm } from "./containerTypes";
import { patchContainerAnswers } from "./api";
import type { ContainerDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./containerTypes", () => ({ getContainerEffectiveForm: vi.fn() }));
vi.mock("./api", () => ({ patchContainerAnswers: vi.fn() }));

const mockedGetEffectiveForm = vi.mocked(getContainerEffectiveForm);
const mockedPatch = vi.mocked(patchContainerAnswers);

registerDefaultFieldRenderers();

const STANDARD = { span: 12, md: 6, lg: 3 };

const layoutJson = JSON.stringify([
  {
    className: null,
    name: "common.header.general",
    isSystemPanel: true,
    canUserAddFields: null,
    rows: [
      {
        columns: [
          { width: STANDARD, hidden: true, isRequired: false, isReadOnly: true, fieldId: -601 },
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: -603 },
        ],
      },
    ],
  },
  {
    className: null,
    name: "common.header.dimensions",
    isSystemPanel: true,
    canUserAddFields: null,
    rows: [],
  },
]);

function field(over: Partial<FieldResource> & { id: string }): FieldResource {
  return {
    resourceType: "fields",
    label: over.id,
    answerType: "TEXT",
    isSystemField: true,
    ...over,
  };
}

const fields: Record<string, FieldResource> = {
  "-601": field({ id: "-601", label: "Identifiant", valueBinding: "identifier" }),
  "-603": field({ id: "-603", label: "Lieu", answerType: "SELECT_ONE_SPATIAL_UNIT", valueBinding: "spatialUnit" }),
};

function container(overrides: Partial<ContainerDetail> = {}): ContainerDetail {
  return {
    resourceType: "containers",
    id: "42",
    identifier: "OA-PROJ-C42",
    projectId: "5",
    organization: { resourceType: "organizations", id: "7" },
    answers: {},
    ...overrides,
  };
}

let containerEl: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

function render(entity: ContainerDetail, onSaved = vi.fn(), writeMode = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <ContainerFicheTab entity={entity} onSaved={onSaved} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  return onSaved;
}

beforeEach(() => {
  mockedGetEffectiveForm.mockReset();
  mockedGetEffectiveForm.mockResolvedValue({ layoutJson, fields });
  mockedPatch.mockReset();
  mockedPatch.mockResolvedValue(container());
  containerEl = document.createElement("div");
  document.body.appendChild(containerEl);
  root = createRoot(containerEl);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  containerEl.remove();
});

describe("ContainerFicheTab", () => {
  it("resolves the effective form for the container's own project and type", async () => {
    render(container({ type: { resourceType: "concepts", id: "9", resolvedLabel: "Caisse" } }));
    await flush();

    expect(mockedGetEffectiveForm).toHaveBeenCalledWith("5", "9");
  });

  it("renders both panels with their labels, and skips hidden columns", async () => {
    render(container());
    await flush();

    expect(containerEl.textContent).toContain("Général");
    expect(containerEl.textContent).toContain("Dimensions");
    expect(containerEl.textContent).not.toContain("Identifiant");
    expect(containerEl.textContent).toContain("Lieu");
  });

  it("shows a warning instead of loading a form when the container has no project", async () => {
    render(container({ projectId: null }));
    await flush();

    expect(containerEl.textContent).toContain("Projet inconnu");
    expect(mockedGetEffectiveForm).not.toHaveBeenCalled();
  });

  it("offers no click-to-edit affordance outside write mode", async () => {
    render(container(), vi.fn(), false);
    await flush();

    const group = Array.from(containerEl.querySelectorAll(".field-value-group")).find((el) =>
      el.textContent?.includes("Lieu"),
    );
    const cell = group?.querySelector(".field-value-cell");
    expect(cell?.classList.contains("field-value-cell-editable")).toBe(false);
  });
});
