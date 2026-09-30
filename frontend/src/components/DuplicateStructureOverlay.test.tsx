import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { DuplicationResult, DuplicationStructure, EntityTypeConfig } from "../entities/types";
import { DuplicateStructureOverlay } from "./DuplicateStructureOverlay";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const loadMock = vi.fn<(id: string | number) => Promise<DuplicationStructure>>();
const runMock = vi.fn<(id: string | number, o: { copies: number; descendantIds: (string | number)[] }) => Promise<DuplicationResult>>();

const config: EntityTypeConfig<unknown, unknown> = {
  key: "dup-entity",
  labels: { singular: "Chose", plural: "Choses" },
  collectionPath: "dup-entities",
  icon: "bi bi-question",
  api: { list: vi.fn(), get: vi.fn() },
  duplication: { load: loadMock, run: runMock, unit: "UE", maxCopies: 50 },
  list: { columns: [], searchable: false },
  detail: { tabs: [] },
  routes: { list: "/x", detail: (id) => `/x/${id}` },
};
registerEntityType(config);

//  1 ── 2 ── 4
//   └── 3
const structure: DuplicationStructure = {
  root: { id: 1, label: "UE-1" },
  descendants: [
    { id: 2, label: "UE-2", parentId: 1 },
    { id: 3, label: "UE-3", parentId: 1 },
    { id: 4, label: "UE-4", parentId: 2 },
  ],
  truncated: false,
};

let container: HTMLDivElement;
let root: Root;
let anchor: HTMLButtonElement;
const onDone = vi.fn();

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((r) => setTimeout(r, 0));
  });
}

async function open() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <DuplicateStructureOverlay entityType="dup-entity" entityId={1} anchor={anchor} onDone={onDone} onHide={() => {}} />
      </QueryClientProvider>,
    );
  });
  await flush();
}

const boxes = () => [...document.querySelectorAll<HTMLInputElement>(".duplicate-structure-tree input[type=checkbox]")].filter((b) => b.id !== "dup-root");
const summary = () => document.querySelector(".create-form-footer-note")?.textContent?.replace(/\s+/g, " ").trim();
const submit = () => [...document.querySelectorAll<HTMLButtonElement>(".create-form-footer button")].find((b) => b.type === "submit")!;

async function tick(box: HTMLInputElement) {
  await act(async () => box.click());
}

beforeEach(() => {
  vi.clearAllMocks();
  loadMock.mockResolvedValue(structure);
  container = document.createElement("div");
  document.body.appendChild(container);
  anchor = document.createElement("button");
  document.body.appendChild(anchor);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  anchor.remove();
  document.body.innerHTML = "";
});

describe("DuplicateStructureOverlay", () => {
  it("loads the structure of the entity and starts with only the root, always copied", async () => {
    await open();

    expect(loadMock).toHaveBeenCalledWith(1);
    expect(document.body.textContent).toContain("Dupliquer la structure");
    const rootBox = document.querySelector<HTMLInputElement>("#dup-root")!;
    expect(rootBox.checked).toBe(true);
    expect(rootBox.disabled).toBe(true);
    expect(boxes()).toHaveLength(3);
    expect(boxes().every((b) => !b.checked)).toBe(true);
    expect(summary()).toBe("1 UE × 1 = 1 nouvelle UE");
    expect(document.body.textContent).not.toContain("Éléments à dupliquer");
  });

  it("ticks a node alone, and the summary counts it", async () => {
    await open();

    await tick(boxes()[0]); // UE-2, not its child UE-4

    expect(boxes().map((b) => b.checked)).toEqual([true, false, false]);
    expect(summary()).toBe("2 UE × 1 = 2 nouvelles UE");
  });

  it("unticks a node with everything under it", async () => {
    await open();
    await act(async () => document.querySelector<HTMLButtonElement>(".duplicate-structure-toggle-all")!.click());

    await tick(boxes()[0]); // UE-2 → UE-4 too

    expect(boxes().map((b) => b.checked)).toEqual([false, true, false]);
  });

  it("ticks the path down to a node, so it stays under its parent's copy", async () => {
    await open();

    await tick(boxes()[2]); // UE-4 → its parent UE-2 too

    expect(boxes().map((b) => b.checked)).toEqual([true, false, true]);
  });

  it("ticks and unticks everything from one link", async () => {
    await open();
    const toggleAll = document.querySelector<HTMLButtonElement>(".duplicate-structure-toggle-all")!;

    await act(async () => toggleAll.click());
    expect(boxes().every((b) => b.checked)).toBe(true);
    expect(toggleAll.textContent).toBe("Tout décocher");

    await act(async () => toggleAll.click());
    expect(boxes().every((b) => !b.checked)).toBe(true);
  });

  it("sends the copies and the ticked descendants, then hands back the result", async () => {
    runMock.mockResolvedValue({ copies: [{ id: 9, label: "UE-9" }], createdCount: 2 });
    await open();
    await tick(boxes()[1]); // UE-3

    await act(async () => submit().click());
    await flush();

    expect(runMock).toHaveBeenCalledWith(1, { copies: 1, descendantIds: [3] });
    expect(onDone).toHaveBeenCalledWith({ copies: [{ id: 9, label: "UE-9" }], createdCount: 2 }, structure.root);
  });

  it("shows the server's refusal and stays open", async () => {
    runMock.mockRejectedValue(new Error("Identifiant généré déjà attribué : UE-9"));
    await open();

    await act(async () => submit().click());
    await flush();

    expect(document.body.textContent).toContain("Identifiant généré déjà attribué : UE-9");
    expect(onDone).not.toHaveBeenCalled();
  });

  it("warns when the structure was cut at the server's limit", async () => {
    loadMock.mockResolvedValue({ ...structure, truncated: true });
    await open();

    expect(document.body.textContent).toContain("trop grande");
  });
});
