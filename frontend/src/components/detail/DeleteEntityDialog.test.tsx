import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { DeleteEntityDialog } from "./DeleteEntityDialog";
import { notifySpy } from "../../test/notifySpy";

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.body.innerHTML = "";
});

function render(props: Partial<React.ComponentProps<typeof DeleteEntityDialog>> = {}) {
  const onConfirm = vi.fn();
  const onHide = vi.fn();
  act(() => {
    root.render(<DeleteEntityDialog visible onConfirm={onConfirm} onHide={onHide} {...props} />);
  });
  return { onConfirm, onHide };
}

const confirmButton = () =>
  [...document.querySelectorAll<HTMLButtonElement>(".delete-entity-dialog button")].find((b) => b.textContent?.includes("Supprimer définitivement"))!;

describe("DeleteEntityDialog", () => {
  it("confirms at once for a plain entity", () => {
    const { onConfirm } = render();
    expect(document.querySelector(".delete-entity-dialog-message")?.textContent).toContain("supprimée définitivement");
    expect(document.querySelector("#delete-entity-name")).toBeNull();
    expect(confirmButton().disabled).toBe(false);
    act(() => confirmButton().click());
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("keeps a project's confirm disabled until its name is typed", () => {
    const { onConfirm } = render({ confirmName: "Fouille 2024" });
    const input = document.querySelector<HTMLInputElement>("#delete-entity-name")!;
    expect(confirmButton().disabled).toBe(true);

    const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!;
    act(() => {
      setValue.call(input, "Fouille 202");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    expect(confirmButton().disabled).toBe(true);

    act(() => {
      setValue.call(input, "Fouille 2024");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    expect(confirmButton().disabled).toBe(false);
    act(() => confirmButton().click());
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it("shows the server's refusal and stays open", () => {
    render({ error: "Impossible de supprimer : le lieu est lié à des documents. Retirez d'abord ces relations." });
    expect(notifySpy.error).toHaveBeenCalledWith(expect.stringContaining("Retirez d'abord"));
  });
});
