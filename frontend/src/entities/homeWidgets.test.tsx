import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { apiFetch } from "../api/client";
import type { HomeWidgetContext, HomeWidgetDef } from "./types";
import { placeHomeWidgets } from "./place/homeWidgets";
import { recordingUnitHomeWidgets } from "./recordingUnit/homeWidgets";
import { findHomeWidgets } from "./find/homeWidgets";
import { phaseHomeWidgets } from "./phase/homeWidgets";
import { containerHomeWidgets } from "./container/homeWidgets";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("../api/client", () => ({ apiFetch: vi.fn() }));

const mockedApiFetch = vi.mocked(apiFetch);

const COUNTS = { projects: 1, places: 2, recordingUnits: 3, finds: 4, phases: 5, containers: 6 };

// The five non-project cards: each shows its own count and opens its organization-wide React list
// through App's router, in homePanel.xhtml's order.
const CASES: [string, (ctx: HomeWidgetContext) => HomeWidgetDef[], string, string, number][] = [
  ["Lieux", placeHomeWidgets, "2", "place", 20],
  ["Unités d'enregistrement", recordingUnitHomeWidgets, "3", "recordingUnit", 30],
  ["Mobilier", findHomeWidgets, "4", "find", 40],
  ["Phases", phaseHomeWidgets, "5", "phase", 50],
  ["Contenants", containerHomeWidgets, "6", "container", 60],
];

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
  mockedApiFetch.mockReset();
  mockedApiFetch.mockResolvedValue({ data: COUNTS });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe.each(CASES)("%s home card", (label, factory, count, entityType, order) => {
  function renderCard(onNavigate = vi.fn()) {
    const [widget] = factory({ organizationId: 7, onNavigate });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(<QueryClientProvider client={queryClient}>{widget.render()}</QueryClientProvider>);
    });
    return { widget, onNavigate };
  }

  it("is a card widget placed in homePanel.xhtml's order", () => {
    const [widget] = factory({ organizationId: 7 });
    expect(widget.kind).toBe("card");
    expect(widget.order).toBe(order);
  });

  it("shows its label and its own count from the organization counts", async () => {
    renderCard();
    await flush();

    expect(mockedApiFetch).toHaveBeenCalledWith("/api/v1/organizations/7/counts");
    expect(container.textContent).toContain(label);
    expect(container.querySelector(".p-chip")!.textContent).toBe(count);
  });

  it("opens its React list on click (no id, no page load)", async () => {
    const { onNavigate } = renderCard();
    await flush();

    expect(container.querySelector("button")).toBeNull();
    await act(async () => {
      container.querySelector('[role="link"]')!.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onNavigate).toHaveBeenCalledWith(entityType);
  });
});
