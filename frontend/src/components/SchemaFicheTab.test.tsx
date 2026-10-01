import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerDefaultFieldRenderers } from "../fields/registerDefaultRenderers";
import type { FieldResource } from "../fields/types";
import { WriteModeProvider } from "../panels/writeMode";
import { getEffectiveForm } from "../entities/typeCatalog";
import { SchemaFicheTab, type SchemaFicheEntity } from "./SchemaFicheTab";

vi.mock("../entities/typeCatalog", () => ({ getEffectiveForm: vi.fn() }));
const mockedGetEffectiveForm = vi.mocked(getEffectiveForm);

registerDefaultFieldRenderers();

const STANDARD = { span: 12, md: 6, lg: 3 };
const FULL = { span: 12, md: 12, lg: 12 };

const layoutJson = JSON.stringify([
  {
    className: null,
    name: "common.header.general",
    isSystemPanel: true,
    rows: [
      {
        columns: [
          { width: STANDARD, hidden: true, isRequired: false, isReadOnly: true, fieldId: -501 },
          { width: FULL, isRequired: false, isReadOnly: false, fieldId: -504 },
          { width: STANDARD, isRequired: false, isReadOnly: false, fieldId: -505 },
        ],
      },
    ],
  },
  { className: null, name: "common.header.chronologie", isSystemPanel: true, rows: [] },
]);

function field(over: Partial<FieldResource> & { id: string }): FieldResource {
  return { resourceType: "fields", label: over.id, answerType: "TEXT", isSystemField: true, ...over };
}

const fields: Record<string, FieldResource> = {
  "-501": field({ id: "-501", label: "Identifiant", valueBinding: "identifier" }),
  "-504": field({ id: "-504", label: "Description", valueBinding: "description" }),
  "-505": field({ id: "-505", label: "Adresse", answerType: "SELECT_ONE_ADDRESS", valueBinding: "address" }),
};

type Entity = SchemaFicheEntity & { answers?: Record<string, unknown> };

function entity(over: Partial<Entity> = {}): Entity {
  return {
    id: "42",
    projectId: "5",
    organization: { id: "7" },
    answers: { "-504": "Une description" },
    ...over,
  };
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function cellOf(label: string): HTMLElement {
  const group = Array.from(container.querySelectorAll(".field-value-group")).find((el) => el.textContent?.includes(label));
  const cell = group?.querySelector(".field-value-cell");
  if (!cell) throw new Error(`No "${label}" field-value-cell`);
  return cell as HTMLElement;
}

function overlayInput(): HTMLInputElement | null {
  return document.body.querySelector(".cell-edit-overlay input") as HTMLInputElement | null;
}

function render(props: { entity: Entity; typesSegment?: string; writeMode?: boolean; isFieldShown?: (f: FieldResource) => boolean }) {
  const save = vi.fn().mockResolvedValue({});
  const onSaved = vi.fn();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <WriteModeProvider value={props.writeMode ?? true}>
          <SchemaFicheTab
            entity={props.entity}
            entityType="phase"
            typesSegment={props.typesSegment}
            save={save}
            onSaved={onSaved}
            isFieldShown={props.isFieldShown}
          />
        </WriteModeProvider>
      </QueryClientProvider>,
    );
  });
  return { save, onSaved };
}

beforeEach(() => {
  mockedGetEffectiveForm.mockReset();
  mockedGetEffectiveForm.mockResolvedValue({ layoutJson, fields });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("SchemaFicheTab — form configured per type", () => {
  it.each(["phase-types", "container-types", "find-types", "recording-unit-types"])(
    "reads the %s catalog for the entity's own project and type",
    async (segment) => {
      render({ entity: entity({ type: { id: "9" } }), typesSegment: segment });
      await flush();

      expect(mockedGetEffectiveForm).toHaveBeenCalledWith(segment, "5", "9");
    },
  );

  it("falls back to the untyped form for an entity without type", async () => {
    render({ entity: entity(), typesSegment: "phase-types" });
    await flush();

    expect(mockedGetEffectiveForm).toHaveBeenCalledWith("phase-types", "5", null);
  });

  it("renders every panel with its label, skips hidden columns, and shows the stored values", async () => {
    render({ entity: entity(), typesSegment: "phase-types" });
    await flush();

    expect(container.textContent).toContain("Général");
    expect(container.textContent).toContain("Chronologie");
    expect(container.textContent).not.toContain("Identifiant");
    expect(cellOf("Description").textContent).toBe("Une description");
  });

  it("saves an edited field through `save`, keyed by the field id", async () => {
    const { save } = render({ entity: entity(), typesSegment: "phase-types" });
    await flush();

    await act(async () => {
      cellOf("Description").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    const input = overlayInput()!;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
    await act(async () => {
      nativeSetter.call(input, "Nouvelle description");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    });
    await flush();

    expect(save).toHaveBeenCalledWith("42", { "-504": { value: "Nouvelle description" } });
  });

  it("offers no click-to-edit affordance outside write mode", async () => {
    render({ entity: entity(), typesSegment: "phase-types", writeMode: false });
    await flush();

    expect(cellOf("Description").classList.contains("field-value-cell-editable")).toBe(false);
    await act(async () => {
      cellOf("Description").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    expect(overlayInput()).toBeNull();
  });

  it("warns instead of loading a form when the entity has no project", async () => {
    render({ entity: entity({ projectId: null }), typesSegment: "phase-types" });
    await flush();

    expect(container.textContent).toContain("Projet inconnu");
    expect(mockedGetEffectiveForm).not.toHaveBeenCalled();
  });
});

describe("SchemaFicheTab — form served with the detail", () => {
  it("renders the entity's own formBundle/fields, with no catalog request", async () => {
    render({ entity: entity({ formBundle: { layoutJson }, fields }) });
    await flush();

    expect(mockedGetEffectiveForm).not.toHaveBeenCalled();
    expect(cellOf("Description").textContent).toBe("Une description");
  });

  it("leaves out the fields `isFieldShown` rejects", async () => {
    render({ entity: entity({ formBundle: { layoutJson }, fields }), isFieldShown: (f) => f.valueBinding !== "address" });
    await flush();

    expect(container.textContent).not.toContain("Adresse");
    expect(container.textContent).toContain("Description");
  });

  it("shows an error instead of a panel when the detail carries no form", async () => {
    render({ entity: entity({ formBundle: null }) });
    await flush();

    expect(container.textContent).toContain("Impossible de charger la configuration du formulaire");
  });
});
