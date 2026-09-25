import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerDefaultFieldRenderers } from "../../fields/registerDefaultRenderers";
import type { FieldResource } from "../../fields/types";
import { WriteModeProvider } from "../../panels/writeMode";
import { PlaceFicheTab } from "./FicheTab";
import { patchPlaceAnswers } from "./api";
import type { PlaceDetail } from "./types";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./api", () => ({ patchPlaceAnswers: vi.fn() }));

const mockedPatch = vi.mocked(patchPlaceAnswers);

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
          { width: STANDARD, isRequired: true, isReadOnly: false, fieldId: -202 },
          { width: STANDARD, isRequired: true, isReadOnly: false, fieldId: -201 },
          { width: STANDARD, isRequired: false, isReadOnly: true, fieldId: -203 },
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: -204 },
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: -205 },
        ],
      },
    ],
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
  "-202": field({ id: "-202", label: "Nom", valueBinding: "name" }),
  "-201": field({ id: "-201", label: "Type", answerType: "SELECT_ONE_FROM_FIELD_CODE", valueBinding: "category" }),
  "-203": field({ id: "-203", label: "Code", valueBinding: "code" }),
  "-204": field({ id: "-204", label: "Adresse", answerType: "SELECT_ONE_ADDRESS", valueBinding: "address" }),
  "-205": field({ id: "-205", label: "Numéro de regroupement", answerType: "INTEGER", valueBinding: "placeNumber" }),
};

function place(overrides: Partial<PlaceDetail> = {}): PlaceDetail {
  return {
    resourceType: "places",
    id: "5",
    name: "Cave A",
    organization: { resourceType: "organizations", id: "7" },
    formBundle: { resourceType: "forms", layoutJson },
    fields,
    answers: { "-202": "Cave A", "-203": "L-5" },
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

function fieldValueGroup(label: string): HTMLElement {
  const group = Array.from(container.querySelectorAll(".field-value-group")).find((el) =>
    el.textContent?.includes(label),
  );
  if (!group) throw new Error(`No field-value-group for "${label}"`);
  return group as HTMLElement;
}

function nameCell(): HTMLElement {
  const cell = fieldValueGroup("Nom").querySelector(".field-value-cell");
  if (!cell) throw new Error("No Nom field-value-cell");
  return cell as HTMLElement;
}

function openName() {
  return act(async () => {
    nameCell().dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

function overlayInput(): HTMLInputElement | null {
  return document.body.querySelector(".cell-edit-overlay input") as HTMLInputElement | null;
}

function render(entity: PlaceDetail, onSaved = vi.fn(), writeMode = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={writeMode}>
          <PlaceFicheTab entity={entity} onSaved={onSaved} />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  return onSaved;
}

beforeEach(() => {
  mockedPatch.mockReset();
  mockedPatch.mockResolvedValue(place());
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

describe("PlaceFicheTab", () => {
  it("renders the panel from entity.formBundle/entity.fields directly, no separate form query", async () => {
    render(place());
    await flush();

    expect(container.textContent).toContain("Général");
    expect(container.textContent).toContain("Nom");
    expect(nameCell().textContent).toBe("Cave A");
  });

  it("skips the address field entirely — not rendered, not editable", async () => {
    render(place());
    await flush();

    expect(container.textContent).not.toContain("Adresse");
  });

  it("persists an edited field through patchPlaceAnswers, keyed by the field id", async () => {
    render(place());
    await flush();

    await openName();
    const input = overlayInput()!;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "Cave B");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    await flush();

    expect(mockedPatch).toHaveBeenCalledWith("5", { "-202": { value: "Cave B" } });
  });

  it("offers no click-to-edit affordance outside write mode", async () => {
    render(place(), vi.fn(), false);
    await flush();

    expect(nameCell().classList.contains("field-value-cell-editable")).toBe(false);
    await act(async () => {
      nameCell().dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(overlayInput()).toBeNull();
  });

  it("shows an error message instead of a panel when there is no formBundle", async () => {
    render(place({ formBundle: null, fields: undefined }));
    await flush();

    expect(container.textContent).toContain("Impossible de charger la configuration du formulaire");
  });
});
