import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { EntityNavigationProvider } from "../../panels/entityNavigation";
import { StratigraphyEditor, type StratigraphyEditorProps } from "./StratigraphyEditor";

const apiFetch = vi.fn();
vi.mock("../../api/client", () => ({ apiFetch: (...args: unknown[]) => apiFetch(...args) }));

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

beforeEach(() => {
  apiFetch.mockReset().mockImplementation(async (url: string) => {
    if (url.includes("/values")) {
      return { data: [{ resourceId: "5", label: "US 5", qualifier: { position: "posterior", concept: { resourceId: "11", label: "coupe" }, uncertain: true } }] };
    }
    if (url.includes("/projects/7/recording-units")) return { data: [{ id: 9, fullIdentifier: "US 9" }, { id: 42, fullIdentifier: "US 42" }] };
    return undefined;
  });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

function render(props: Partial<StratigraphyEditorProps> = {}, openEntity = vi.fn()) {
  const onChanged = vi.fn();
  const onClose = vi.fn();
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <EntityNavigationProvider value={openEntity}>
          <StratigraphyEditor fieldId="-329" position="posterior" unitId="42" projectId="7" organizationId={1} typeConceptId="900" onChanged={onChanged} onClose={onClose} {...props} />
        </EntityNavigationProvider>
      </QueryClientProvider>,
    );
  });
  return { onChanged, onClose, openEntity };
}

const button = (label: string) => container.querySelector(`button[aria-label='${label}']`) as HTMLElement;

describe("StratigraphyEditor", () => {
  it("shows the relationships of its field as chips, with their type and uncertainty, and no header or footer", async () => {
    render();
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42/fields/-329/values?offset=0&limit=200");
    const chip = container.querySelector(".ref-chip")!;
    expect(chip.textContent).toContain("US 5");
    expect(chip.textContent).toContain("coupe");
    expect(chip.textContent).toContain("?");
    expect(container.querySelector(".sia-create-form-title, .sia-create-form-footer")).toBeNull();
  });

  it("opens the fiche of a chip's unit and closes the overlay", async () => {
    const { openEntity, onClose } = render();
    await flush();

    await act(async () => {
      (container.querySelector("[role=link]") as HTMLElement).click();
    });

    expect(openEntity).toHaveBeenCalledWith("recordingUnit", "5");
    expect(onClose).toHaveBeenCalled();
  });

  it("removes a relationship at once, from its chip's cross, then tells the fiche", async () => {
    const { onChanged } = render();
    await flush();

    await act(async () => {
      button("Supprimer le lien").click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42/stratigraphic-relationships/5", { method: "DELETE" });
    expect(onChanged).toHaveBeenCalled();
  });

  it("edits a relationship's type and uncertainty from its chip's pencil, and offers its field's types", async () => {
    const { onChanged } = render();
    await flush();

    await act(async () => {
      button("Modifier le lien").click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/organizations/1/concepts?fieldId=-329&projectId=7&valueConceptId=900");
    expect((container.querySelector(".sia-strati-form input[type=checkbox]") as HTMLInputElement).checked).toBe(true);

    await act(async () => {
      (container.querySelector(".sia-strati-form input[type=checkbox]") as HTMLElement).click();
    });
    await act(async () => {
      button("Confirmer").click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42/stratigraphic-relationships/5", {
      method: "PUT",
      body: { typeConceptId: 11, uncertain: false },
    });
    expect(onChanged).toHaveBeenCalled();
  });

  it("adds a unit picked in the search — never the edited unit itself — once its type and uncertainty are confirmed", async () => {
    const { onChanged } = render();
    await flush();

    await act(async () => {
      (container.querySelector(".sia-inline-picker input") as HTMLInputElement).focus();
    });
    await flush();
    const options = [...container.querySelectorAll(".sia-inline-picker-item")].map((el) => el.textContent);
    expect(options).toEqual(["US 9"]);

    await act(async () => {
      (container.querySelector(".sia-inline-picker-item") as HTMLElement).click();
    });
    // Picked, not linked yet: the link waits for the confirmation.
    expect(apiFetch).not.toHaveBeenCalledWith(expect.stringContaining("stratigraphic-relationships"), expect.objectContaining({ method: "POST" }));
    await act(async () => {
      button("Confirmer").click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42/stratigraphic-relationships", {
      method: "POST",
      body: { relatedRecordingUnitId: 9, position: "posterior", typeConceptId: null, uncertain: false },
    });
    expect(onChanged).toHaveBeenCalled();
  });
});
