import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { deleteFicheConfig, putFicheConfig } from "../api/ficheConfigs";
import type { DetailTabDef } from "../entities/types";
import { FicheTabsEditor } from "./FicheTabsEditor";

const code = [
  { key: "fiche", label: "Fiche", render: () => null },
  { key: "docs", label: "Documents", relation: { target: "not-registered" }, render: () => null },
] as DetailTabDef<never>[];

let container: HTMLDivElement;
let root: Root;
const onHide = vi.fn();
const onChanged = vi.fn();

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderEditor(props: { saved?: Record<string, unknown>; typeId?: number; savedTypeId?: number } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <FicheTabsEditor
          entityType="recordingUnit"
          entityId="7"
          organizationId={4}
          scope={{ projectId: 11, typeId: props.typeId }}
          codeTabs={code}
          saved={props.saved}
          savedTypeId={props.savedTypeId}
          onHide={onHide}
          onChanged={onChanged}
        />
      </QueryClientProvider>,
    );
  });
  await flush();
}

const dialog = () => document.body.querySelector<HTMLElement>(".p-dialog")!;
const buttonNamed = (label: string) =>
  Array.from(dialog().querySelectorAll<HTMLButtonElement>("button")).find((b) => b.textContent?.trim() === label || b.getAttribute("aria-label") === label)!;
const nameInputs = () => Array.from(dialog().querySelectorAll<HTMLInputElement>("input.fiche-tabs-editor-name"));
const removeButtons = () => Array.from(dialog().querySelectorAll<HTMLButtonElement>("button[aria-label=\"Supprimer l'onglet\"]"));

async function click(el: HTMLElement) {
  await act(async () => {
    el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  await flush();
}

async function type(input: HTMLInputElement, value: string) {
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(input, value);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

beforeEach(() => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  onHide.mockClear();
  onChanged.mockClear();
  vi.mocked(putFicheConfig).mockClear();
  vi.mocked(deleteFicheConfig).mockClear();
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.body.innerHTML = "";
});

describe("FicheTabsEditor", () => {
  it("lists the fiche's tabs, the code's own when nothing is configured; only the lists can be removed", async () => {
    await renderEditor();
    expect(nameInputs().map((i) => i.placeholder)).toEqual(["Fiche", "Documents"]);
    // Details and graph-like tabs stay: one bin, on the relation tab.
    expect(removeButtons()).toHaveLength(1);
    expect(nameInputs()[1].closest("li")!.contains(removeButtons()[0])).toBe(true);
  });

  it("saves the order, names and removals as one configuration for every sub-type of a fiche without one", async () => {
    await renderEditor();

    await click(buttonNamed("Descendre"));
    await type(nameInputs()[0], "Pièces jointes");
    await click(removeButtons()[0]);
    await click(buttonNamed("Enregistrer"));

    expect(putFicheConfig).toHaveBeenCalledWith(
      { organizationId: 4, resourceType: "recordingUnit", projectId: 11, typeId: undefined },
      { v: 1, tabs: [{ key: "docs", label: "Pièces jointes", hidden: true }, { key: "fiche" }] },
    );
    expect(onChanged).toHaveBeenCalled();
    expect(onHide).toHaveBeenCalled();
  });

  it("saves for the fiche's sub-type when it has one", async () => {
    await renderEditor({ typeId: 21 });
    await click(buttonNamed("Enregistrer"));
    expect(putFicheConfig).toHaveBeenLastCalledWith(expect.objectContaining({ typeId: 21 }), expect.anything());
  });

  it("has no sub-type checkbox", async () => {
    await renderEditor({ typeId: 21 });
    expect(dialog().querySelector("#fiche-tabs-subtype")).toBeNull();
  });

  it("puts a removed built-in tab back from the « Ajouter un onglet » menu", async () => {
    await renderEditor({ saved: { tabs: [{ key: "fiche" }, { key: "docs", hidden: true }] } });
    expect(nameInputs().map((i) => i.placeholder)).toEqual(["Fiche"]);

    await click(buttonNamed("Ajouter un onglet"));
    const item = Array.from(document.body.querySelectorAll<HTMLElement>(".p-menuitem-link")).find((a) => a.textContent?.includes("Documents"))!;
    await click(item);

    expect(nameInputs().map((i) => i.placeholder)).toEqual(["Fiche", "Documents"]);
  });

  it("goes back to the code's tabs by removing the configuration in force, only when there is one", async () => {
    await renderEditor();
    expect(buttonNamed("Revenir aux onglets par défaut").disabled).toBe(true);

    act(() => root.render(null));
    document.body.innerHTML = "";
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
    await renderEditor({ typeId: 21, saved: { tabs: [{ key: "docs" }] }, savedTypeId: 21 });
    await click(buttonNamed("Revenir aux onglets par défaut"));

    expect(deleteFicheConfig).toHaveBeenCalledWith({ organizationId: 4, resourceType: "recordingUnit", projectId: 11, typeId: 21 });
    expect(onHide).toHaveBeenCalled();
  });

  it("removes the configuration made for every sub-type, not the one of this sub-type, when that is the one in force", async () => {
    await renderEditor({ typeId: 21, saved: { tabs: [{ key: "docs" }] }, savedTypeId: undefined });
    await click(buttonNamed("Revenir aux onglets par défaut"));

    expect(deleteFicheConfig).toHaveBeenCalledWith({ organizationId: 4, resourceType: "recordingUnit", projectId: 11, typeId: undefined });
  });

  it("stays open when saving fails", async () => {
    vi.mocked(putFicheConfig).mockRejectedValueOnce(new Error("403"));
    await renderEditor();
    await click(buttonNamed("Enregistrer"));

    expect(onHide).not.toHaveBeenCalled();
    expect(onChanged).not.toHaveBeenCalled();
  });
});
