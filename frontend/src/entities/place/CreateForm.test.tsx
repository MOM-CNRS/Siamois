import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldRendererProps } from "../../fields/registry";
import { PlaceCreateForm } from "./CreateForm";
import type { CreatePrefill } from "../types";
import { createPlace } from "./api";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./api", () => ({ createPlace: vi.fn() }));

// Stand-in for the concept picker: records the field it was given, and picks a type on click.
const pickerField = vi.fn();
vi.mock("../../fields/renderers", () => ({
  SelectOneConceptRenderer: ({ field, onChange }: FieldRendererProps) => {
    pickerField(field);
    return (
      <button type="button" data-testid="pick-type" onClick={() => onChange({ resourceId: "9", resourceType: "concepts", label: "Grotte" })}>
        Choisir un type
      </button>
    );
  },
}));

const mockedCreatePlace = vi.mocked(createPlace);

let container: HTMLDivElement;
let root: Root;

function render(onCreated = vi.fn(), onCancel = vi.fn(), prefill?: CreatePrefill) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <PlaceCreateForm organizationId={7} prefill={prefill} onCreated={onCreated} onCancel={onCancel} />
      </QueryClientProvider>,
    );
  });
  return { onCreated, onCancel };
}

function button(label: string): HTMLButtonElement {
  return Array.from(container.querySelectorAll("button")).find((b) => b.textContent === label) as HTMLButtonElement;
}

function typeName(value: string) {
  const input = container.querySelector("input")!;
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!;
  act(() => {
    setter.call(input, value);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

beforeEach(() => {
  mockedCreatePlace.mockReset();
  pickerField.mockReset();
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("PlaceCreateForm", () => {
  it("queries place types through the SIASU.TYPE field code", () => {
    render();

    expect(pickerField).toHaveBeenCalledWith(expect.objectContaining({ fieldCode: "SIASU.TYPE" }));
  });

  it("disables submit until both a name and a type are given", () => {
    render();
    expect(button("Créer").disabled).toBe(true);

    typeName("Cave A");
    expect(button("Créer").disabled).toBe(true);

    act(() => container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click());
    expect(button("Créer").disabled).toBe(false);
  });

  it("creates the place in the list's organization and hands its id to onCreated", async () => {
    mockedCreatePlace.mockResolvedValue({ id: 42 });
    const { onCreated } = render();

    typeName("  Cave A  ");
    act(() => container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click());
    await act(async () => {
      button("Créer").dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });

    expect(mockedCreatePlace).toHaveBeenCalledWith({ organizationId: 7, name: "Cave A", typeConceptId: "9" });
    expect(onCreated).toHaveBeenCalledWith(42);
  });

  it("links the new place as the child of a row action's parent", async () => {
    mockedCreatePlace.mockResolvedValue({ id: 43 });
    render(vi.fn(), vi.fn(), { parent: { id: "8", label: "Site B" } });

    expect(container.textContent).toContain("Contenu dans");
    typeName("Cave C");
    act(() => container.querySelector<HTMLButtonElement>('[data-testid="pick-type"]')!.click());
    await act(async () => {
      button("Créer").dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true }));
    });

    expect(mockedCreatePlace).toHaveBeenCalledWith({
      organizationId: 7, name: "Cave C", typeConceptId: "9", parentPlaceId: "8", childPlaceId: undefined,
    });
  });

    it("calls onCancel from the cancel button", () => {
    const { onCancel } = render();

    act(() => button("Annuler").click());

    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
