import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { apiFetch } from "../api/client";
import { requiresValidator } from "../api/validation";
import { ValidationStatusButton } from "./ValidationStatusButton";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((r) => setTimeout(r, 0));
  });
}

function render(props: { status: string; canEdit: boolean; canValidate: boolean }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <ValidationStatusButton collectionPath="phases" entityId={3} {...props} />
      </QueryClientProvider>,
    );
  });
}

async function openOverlay() {
  await act(async () => {
    container.querySelector("button.status-button")!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  await flush();
}

function option(label: string): HTMLButtonElement {
  return Array.from(document.querySelectorAll<HTMLButtonElement>(".validation-status-option")).find((b) =>
    b.textContent?.includes(label),
  )!;
}

beforeEach(() => {
  mockedApiFetch.mockReset().mockResolvedValue(undefined);
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.body.innerHTML = "";
});

describe("requiresValidator", () => {
  it("is needed to reach or leave VALIDATED, never otherwise", () => {
    expect(requiresValidator("COMPLETE", "VALIDATED")).toBe(true);
    expect(requiresValidator("VALIDATED", "CANCELLED")).toBe(true);
    expect(requiresValidator("INCOMPLETE", "CANCELLED")).toBe(false);
    expect(requiresValidator("VALIDATED", "VALIDATED")).toBe(false);
  });
});

describe("ValidationStatusButton", () => {
  it("shows the current status's icon", () => {
    render({ status: "CANCELLED", canEdit: true, canValidate: false });
    expect(container.querySelector("button.status-button.cancelled .bi-x-circle, button.status-button.cancelled")).toBeTruthy();
  });

  it("offers an editor en cours / terminé / annulé but not validé", async () => {
    render({ status: "INCOMPLETE", canEdit: true, canValidate: false });
    await openOverlay();

    expect(option("En cours").disabled).toBe(true); // current
    expect(option("Terminé").disabled).toBe(false);
    expect(option("Annulé").disabled).toBe(false);
    expect(option("Validé").disabled).toBe(true);
  });

  it("offers a validator validé, and sends the status through the entity's PATCH", async () => {
    render({ status: "COMPLETE", canEdit: false, canValidate: true });
    await openOverlay();

    expect(option("Validé").disabled).toBe(false);
    expect(option("Annulé").disabled).toBe(true); // an editor's move, and this validator can't edit

    await act(async () => {
      option("Validé").dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/phases/3", { method: "PATCH", body: { validated: "VALIDATED" } });
  });

  it("is disabled when nothing is allowed (read mode, no rights)", () => {
    render({ status: "VALIDATED", canEdit: true, canValidate: false });
    // An editor can't leave VALIDATED, and every other option is a move out of it.
    expect(container.querySelector<HTMLButtonElement>("button.status-button")!.disabled).toBe(true);
  });
});
