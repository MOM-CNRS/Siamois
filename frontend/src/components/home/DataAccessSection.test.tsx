import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {apiFetch} from "../../api/client";
import {registerEntityType} from "../../entities/registry";
import type {EntityTypeConfig} from "../../entities/shared/types";
import {DataAccessSection} from "./DataAccessSection";

vi.mock("../../api/client", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

const listMock = vi.fn(async () => ({ data: [], totalCount: 42 }));

const catalog = {
  fields: { type: { id: "type", answerType: "SELECT_ONE", fieldCode: "SIAMOIS.RUTYPE", valueBinding: "type" } },
  columns: [],
};

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 6; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function render(ctx: Parameters<typeof DataAccessSection>[0]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <DataAccessSection {...ctx} />
      </QueryClientProvider>,
    );
  });
}

const accesses = (canEdit: boolean, data: unknown[] = [], defaults = false) =>
  mockedApiFetch.mockImplementation(async (path: string, init?: { method?: string }) => {
    if (path.startsWith("/api/v1/home-accesses") && (init?.method ?? "GET") === "GET") return { data, canEdit, defaults };
    if (path.includes("/concepts")) return { data: [{ id: "412", resolvedLabel: "Couche" }, { id: "413", resolvedLabel: "Fosse" }] };
    return {};
  });

const button = (label: string) =>
  Array.from(document.querySelectorAll("button")).find((b) => b.textContent?.includes(label) || b.getAttribute("aria-label") === label);

beforeAll(() => {
  registerEntityType({
    key: "recordingUnit",
    icon: "bi bi-pencil-square",
    cardClassName: "sia-recording-unit",
    labels: { singular: "Unité d'enregistrement", plural: "Unités d'enregistrement", short: "UE" },
    routes: { list: "/recording-unit", detail: (id: string | number) => `/recording-unit/${id}` },
    list: { schema: { load: async () => catalog } },
    api: { list: listMock },
    home: {
      widgets: (ctx: { onNavigate?: (type: string) => void }) => [
        { key: "recordingUnit-count", kind: "card", render: () => <button type="button" role="link" className="sia-clickable-card" onClick={() => ctx.onNavigate?.("recordingUnit")}>Table des UE</button> },
      ],
    },
  } as unknown as EntityTypeConfig<unknown, unknown>);
});

beforeEach(() => {
  mockedApiFetch.mockReset();
  listMock.mockClear();
  document.body.innerHTML = "";
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
});

describe("DataAccessSection", () => {
  it("is not there for a member when nothing is set up", async () => {
    accesses(false);
    render({ organizationId: 7 });
    await flush();

    expect(container.textContent).toBe("");
  });

  it("shows the cards in the colour of their table, opening the table on their view", async () => {
    accesses(false, [{ id: 1, viewId: 11, resourceType: "recordingUnit", title: "UE – Couche", label: "Couche", filters: { type: { op: "in", v: ["412"] } } }]);
    const onOpenView = vi.fn();
    render({ organizationId: 7, onOpenView });
    await flush();
    // The count is asked once the cards are known.
    await flush();

    const card = container.querySelector<HTMLElement>(".sia-clickable-card")!;
    expect(card.querySelector(".sia-welcome-card")?.className).toContain("sia-recording-unit");
    expect(card.querySelector("i")?.className).toBe("bi bi-pencil-square");
    // The view's name, with how many there are under the view's own filters.
    expect(card.textContent).toBe("UE – Couche42");
    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ organizationId: 7, filters: { type: { op: "in", v: ["412"] } } }));
    expect(button("Modifier")).toBeUndefined();
    expect(button("Retirer de la une")).toBeUndefined();

    act(() => card.click());
    expect(onOpenView).toHaveBeenCalledWith("recordingUnit", 11);
  });

  it("lets a manager remove a card without opening it", async () => {
    accesses(true, [{ id: 1, viewId: 11, resourceType: "recordingUnit", title: "UE – Couche", label: "Couche", filters: { type: { op: "in", v: ["412"] } } }]);
    const onOpenView = vi.fn();
    render({ organizationId: 7, onOpenView });
    await flush();

    await act(async () => button("Modifier")!.click());
    await act(async () => button("Retirer de la une")!.click());
    expect(mockedApiFetch).not.toHaveBeenCalledWith("/api/v1/home-accesses/1?organizationId=7", { method: "DELETE" });
    // The confirmation dialog's own button comes after the card's.
    const confirms = Array.from(document.querySelectorAll("button")).filter((b) => b.textContent?.includes("Retirer de la une"));
    await act(async () => confirms[confirms.length - 1].click());

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/home-accesses/1?organizationId=7", { method: "DELETE" });
    expect(onOpenView).not.toHaveBeenCalled();
  });

  it("shows a manager the empty section, with « Modifier » in its header", async () => {
    accesses(true);
    render({ organizationId: 7 });
    await flush();

    expect(container.textContent).toContain("Vues épinglées par l'organisation");
    expect(container.textContent).toContain("Aucun accès aux données.");
    expect(button("Modifier")).toBeDefined();
  });

  it("adding a table and a type creates the access with a view filtered on the type", async () => {
    accesses(true);
    render({ organizationId: 7 });
    await flush();
    await act(async () => button("Modifier")!.click());
    // The last card searches the existing views; creating one is the footer of that list.
    await act(async () => document.querySelector<HTMLElement>(".p-dropdown")!.click());
    await flush();
    await act(async () => button("Créer une nouvelle vue")!.click());
    await flush();

    // The type dropdown offers the table's types, read from its type field's concepts.
    const typeDropdown = document.querySelector<HTMLElement>("#home-data-access-type")!.closest(".p-dropdown")!;
    await act(async () => (typeDropdown.querySelector(".p-dropdown-trigger") as HTMLElement).click());
    await flush();
    const option = Array.from(document.querySelectorAll<HTMLElement>(".p-dropdown-item")).find((o) => o.textContent === "Couche")!;
    await act(async () => option.click());

    await act(async () => Array.from(document.querySelectorAll<HTMLButtonElement>(".home-data-access-actions button")).pop()!.click());
    await flush();

    const post = mockedApiFetch.mock.calls.find(([path, init]) => path === "/api/v1/home-accesses" && init?.method === "POST")!;
    expect(post[1]).toEqual({
      method: "POST",
      body: {
        organizationId: 7,
        resourceType: "recordingUnit",
        title: "UE – Couche",
        state: { listFilters: { type: { op: "in", v: ["412"], labels: { "412": "Couche" } } } },
      },
    });
  });

  it("the name of the view can be edited in the creation dialog, and is what the card says", async () => {
    accesses(true);
    render({ organizationId: 7 });
    await flush();
    await act(async () => button("Modifier")!.click());
    // The last card searches the existing views; creating one is the footer of that list.
    await act(async () => document.querySelector<HTMLElement>(".p-dropdown")!.click());
    await flush();
    await act(async () => button("Créer une nouvelle vue")!.click());
    await flush();

    // The type dropdown offers the table's types, read from its type field's concepts.
    const typeDropdown = document.querySelector<HTMLElement>("#home-data-access-type")!.closest(".p-dropdown")!;
    await act(async () => (typeDropdown.querySelector(".p-dropdown-trigger") as HTMLElement).click());
    await flush();
    const option = Array.from(document.querySelectorAll<HTMLElement>(".p-dropdown-item")).find((o) => o.textContent === "Couche")!;
    await act(async () => option.click());

    const name = document.querySelector<HTMLInputElement>("#home-data-access-name")!;
    expect(name.value).toBe("UE – Couche");
    await act(async () => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")!.set!.call(name, "Mes couches");
      name.dispatchEvent(new Event("input", { bubbles: true }));
    });

    await act(async () => Array.from(document.querySelectorAll<HTMLButtonElement>(".home-data-access-actions button")).pop()!.click());
    await flush();

    const post = mockedApiFetch.mock.calls.find(([path, init]) => path === "/api/v1/home-accesses" && init?.method === "POST")!;
    expect(post[1]).toEqual({
      method: "POST",
      body: {
        organizationId: 7,
        resourceType: "recordingUnit",
        title: "Mes couches",
        state: { listFilters: { type: { op: "in", v: ["412"], labels: { "412": "Couche" } } } },
      },
    });
  });

  it("« Voir toutes les vues » opens the views drawer of the sidebar, for members too", async () => {
    accesses(false, [{ id: 1, viewId: 11, resourceType: "recordingUnit", title: "UE – Couche", label: "Couche" }]);
    const link = document.createElement("a");
    link.id = "navForm:viewsLinkNav";
    const clicked = vi.fn();
    link.addEventListener("click", clicked);
    document.body.appendChild(link);
    render({ organizationId: 7 });
    await flush();

    await act(async () => button("Voir toutes les vues")!.click());

    expect(clicked).toHaveBeenCalledTimes(1);
  });

  it("cannot add before a type is chosen", async () => {
    accesses(true);
    render({ organizationId: 7 });
    await flush();
    await act(async () => button("Modifier")!.click());
    // The last card searches the existing views; creating one is the footer of that list.
    await act(async () => document.querySelector<HTMLElement>(".p-dropdown")!.click());
    await flush();
    await act(async () => button("Créer une nouvelle vue")!.click());
    await flush();

    const add = Array.from(document.querySelectorAll<HTMLButtonElement>(".home-data-access-actions button")).pop()!;
    expect(add.disabled).toBe(true);
    expect(mockedApiFetch.mock.calls.some(([path, init]) => path === "/api/v1/home-accesses" && init?.method === "POST")).toBe(false);
  });

  it("shows a table card, with no view, from its entity's home card, opening the table", async () => {
    accesses(false, [{ id: 4, viewId: null, resourceType: "recordingUnit", title: null, label: null }]);
    const onNavigate = vi.fn();
    render({ organizationId: 7, onNavigate });
    await flush();

    expect(container.textContent).toContain("Table des UE");
    expect(listMock).not.toHaveBeenCalled();
    act(() => container.querySelector<HTMLElement>(".sia-clickable-card, [role=link]")!.click());
    expect(onNavigate).toHaveBeenCalledWith("recordingUnit");
  });

  it("stores the default cards when a manager starts editing them", async () => {
    mockedApiFetch.mockImplementation(async (path: string, init?: { method?: string }) => {
      if (path.startsWith("/api/v1/home-accesses/customize")) {
        return { data: [{ id: 4, viewId: null, resourceType: "recordingUnit", title: null, label: null }], canEdit: true, defaults: false };
      }
      if (path.startsWith("/api/v1/home-accesses") && (init?.method ?? "GET") === "GET") {
        return { data: [{ id: null, viewId: null, resourceType: "recordingUnit", title: null, label: null }], canEdit: true, defaults: true };
      }
      return {};
    });
    render({ organizationId: 7 });
    await flush();

    // No card can be changed while they are only the defaults.
    expect(button("Revenir aux tables")).toBeUndefined();
    await act(async () => button("Modifier")!.click());
    await flush();

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/home-accesses/customize?organizationId=7", { method: "POST" });
    expect(button("Revenir aux tables")).toBeDefined();
    expect(button("Retirer de la une")).toBeDefined();
  });

  it("goes back to the tables on request", async () => {
    accesses(true, [{ id: 1, viewId: 11, resourceType: "recordingUnit", title: "UE – Couche", label: "Couche" }]);
    render({ organizationId: 7 });
    await flush();
    await act(async () => button("Modifier")!.click());
    await act(async () => button("Revenir aux tables")!.click());
    const confirms = Array.from(document.querySelectorAll("button")).filter((b) => b.textContent?.includes("Revenir aux tables"));
    await act(async () => confirms[confirms.length - 1].click());

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/home-accesses?organizationId=7", { method: "DELETE" });
  });
});
