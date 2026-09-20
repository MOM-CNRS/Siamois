import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react-dom/test-utils";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { registerEntityType } from "../entities/registry";
import type { EntityTypeConfig } from "../entities/types";
import { EntityDetailPanel } from "./EntityDetailPanel";

// Generic-component test (plan §8 phase 6), registered against a throwaway fake entity type —
// entities/project/config.tsx and FicheTab.tsx already have their own coverage. This exercises
// the mechanism EntityDetailPanel itself is responsible for: rendering config.detail.tabs and
// handing each one a working `refetch` helper (plan §8 phase 6 — added so a tab that mutates the
// entity, like Project's fiche, can ask the panel's own query to reload).

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

interface FakeEntity {
  id: string;
  name: string;
}

const getMock = vi.fn<(id: string | number) => Promise<FakeEntity>>();

const fakeConfig: EntityTypeConfig<FakeEntity, FakeEntity> = {
  key: "fake-detail-entity",
  labels: { singular: "Fake", plural: "Fakes" },
  api: { list: vi.fn(), get: getMock },
  list: { columns: [], searchable: false },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: "Fiche",
        render: (entity, helpers) => (
          <div>
            <span data-testid="name">{entity.name}</span>
            <button onClick={() => helpers.refetch()}>refresh</button>
          </div>
        ),
      },
    ],
  },
  routes: { list: "/fake", detail: (id) => `/fake/${id}` },
};

registerEntityType(fakeConfig);

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setTimeout(resolve, 0));
    }
  });
}

beforeEach(() => {
  getMock.mockReset();
  getMock.mockResolvedValue({ id: "1", name: "First" });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => {
    root.unmount();
  });
  container.remove();
});

describe("EntityDetailPanel", () => {
  it("renders the registered tab with the fetched entity", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(getMock).toHaveBeenCalledWith("1");
    expect(container.textContent).toContain("First");
  });

  it("re-invokes config.api.get when the tab calls the refetch helper", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="fake-detail-entity" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();
    expect(getMock).toHaveBeenCalledTimes(1);

    const button = container.querySelector("button") as HTMLElement;
    await act(async () => {
      button.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });
    await flush();

    expect(getMock).toHaveBeenCalledTimes(2);
  });

  it("shows an unsupported message for an unregistered entity type", async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    act(() => {
      root.render(
        <QueryClientProvider client={queryClient}>
          <EntityDetailPanel entityType="does-not-exist" entityId="1" />
        </QueryClientProvider>,
      );
    });
    await flush();

    expect(container.textContent).toContain("does-not-exist");
  });
});
