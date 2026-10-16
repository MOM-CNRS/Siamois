import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { FieldResource } from "../../fields/types";
import { StratigraphyCell } from "./StratigraphyCell";

const apiFetch = vi.fn();
vi.mock("../../api/client", () => ({ apiFetch: (...args: unknown[]) => apiFetch(...args) }));

const field = { id: "-329", resourceType: "fields", label: "Postérieur", answerType: "SELECT_MULTIPLE_STRATIGRAPHY", isSystemField: true, valueBinding: "posteriorRelationships" } as FieldResource;
const stored = { values: [{ resourceId: "5", resourceType: "recording-units", label: "US 5" }], total: 1, complete: true };

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

beforeEach(() => {
  apiFetch.mockReset().mockResolvedValue({
    data: [{ resourceId: "5", label: "US 5", qualifier: { position: "posterior", concept: { label: "coupe" }, uncertain: true } }],
  });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.querySelectorAll(".p-overlaypanel").forEach((el) => el.remove());
});

function render(editable: boolean) {
  const onChanged = vi.fn();
  act(() => {
    root.render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <StratigraphyCell field={field} unitId="42" stored={stored} projectId="7" organizationId={1} typeConceptId="900" editable={editable} onChanged={onChanged} />
      </QueryClientProvider>,
    );
  });
  return { onChanged };
}

describe("StratigraphyCell", () => {
  it("shows the relationships and offers no editing to a reader", () => {
    render(false);

    expect(container.textContent).toContain("US 5");
    expect(container.querySelector("[role=button]")).toBeNull();
  });

  it("opens an editor listing the relationships of its side of the unit, to a writer", async () => {
    render(true);
    await act(async () => {
      (container.querySelector("[role=button]") as HTMLElement).click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42/fields/-329/values?offset=0&limit=200");
    const editor = document.querySelector(".p-overlaypanel")!;
    expect(editor.textContent).toContain("US 5");
    expect(editor.textContent).toContain("coupe");
    expect(editor.querySelectorAll("button[aria-label]").length).toBeGreaterThanOrEqual(2);
  });

  it("offers the relationship types of its own field's list, narrowed by the unit's type", async () => {
    render(true);
    await act(async () => {
      (container.querySelector("[role=button]") as HTMLElement).click();
    });
    await flush();

    await act(async () => {
      (document.querySelector(".sia-link-button") as HTMLElement).click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/organizations/1/concepts?fieldId=-329&projectId=7&valueConceptId=900");
  });

  it("shows a field that is not a side of the stratigraphy without editing", () => {
    act(() => {
      root.render(
        <QueryClientProvider client={new QueryClient()}>
          <StratigraphyCell field={{ ...field, valueBinding: "other" }} unitId="42" stored={stored} editable onChanged={vi.fn()} />
        </QueryClientProvider>,
      );
    });

    expect(container.textContent).toContain("US 5");
    expect(container.querySelector("[role=button]")).toBeNull();
  });

  it("removes a relationship only after a confirmation, then tells the fiche", async () => {
    const { onChanged } = render(true);
    await act(async () => {
      (container.querySelector("[role=button]") as HTMLElement).click();
    });
    await flush();
    const editor = document.querySelector(".p-overlaypanel")!;

    await act(async () => {
      (editor.querySelector("button[aria-label='Supprimer le lien']") as HTMLElement).click();
    });
    expect(apiFetch).not.toHaveBeenCalledWith(expect.stringContaining("stratigraphic-relationships"), expect.anything());

    await act(async () => {
      (editor.querySelector("button[aria-label='Confirmer']") as HTMLElement).click();
    });
    await flush();

    expect(apiFetch).toHaveBeenCalledWith("/api/v1/recording-units/42/stratigraphic-relationships/5", { method: "DELETE" });
    expect(onChanged).toHaveBeenCalled();
  });
});
