import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {act} from "react";
import {createRoot, type Root} from "react-dom/client";
import {QueryClient, QueryClientProvider} from "@tanstack/react-query";
import {queryKeys} from "../../api/queryKeys";
import type {EntityTypeConfig} from "../../entities/shared/types";
import {ListToolbar} from "./ListToolbar";

vi.mock("../../viewport", () => ({ useIsMobile: () => false }));
vi.mock("primereact/toolbar", () => ({
  Toolbar: ({ end }: { end: React.ReactNode }) => <div data-testid="toolbar-end">{end}</div>,
}));
vi.mock("primereact/overlaypanel", async () => {
  const React = await import("react");
  return {
    OverlayPanel: React.forwardRef(function OverlayPanel(
      { children }: { children: React.ReactNode },
      ref: React.ForwardedRef<{ toggle: () => void; hide: () => void }>,
    ) {
      React.useImperativeHandle(ref, () => ({ toggle: () => {}, hide: () => {} }));
      return <div data-testid="create-overlay">{children}</div>;
    }),
  };
});
vi.mock("primereact/button", () => ({
  Button: ({ label, onClick }: { label?: string; onClick?: (e: { currentTarget: HTMLElement }) => void }) => (
    <button
      type="button"
      data-testid={label === "list.create" || label?.includes("Créer") || label?.includes("Create") ? "create" : "btn"}
      onClick={(e) => onClick?.({ currentTarget: e.currentTarget })}
    >
      {label}
    </button>
  ),
}));
vi.mock("primereact/chip", () => ({ Chip: () => null }));
vi.mock("primereact/skeleton", () => ({ Skeleton: () => null }));
vi.mock("primereact/tooltip", () => ({ Tooltip: () => null }));
vi.mock("../../components/layout/BottomSheet", () => ({ BottomSheet: () => null }));
vi.mock("../../i18n", () => ({ t: (key: string) => key }));

let container: HTMLDivElement;
let root: Root;
let queryClient: QueryClient;

const config = {
  labels: { singular: "Document", plural: "Documents" },
  list: {
    createForm: ({ onCreated }: { onCreated: (id: string) => void }) => (
      <button type="button" data-testid="submit-create" onClick={() => onCreated("99")}>
        submit
      </button>
    ),
  },
} as unknown as EntityTypeConfig<unknown, unknown>;

function render(props: Partial<Parameters<typeof ListToolbar>[0]> = {}) {
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <ListToolbar
          entityType="document"
          config={config}
          organizationId={1}
          start={null}
          isLoading={false}
          totalCount={0}
          selectedCount={0}
          onClearSelection={() => {}}
          createBlocked={null}
          onCreated={() => {}}
          {...props}
        />
      </QueryClientProvider>,
    );
  });
}

beforeEach(() => {
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

describe("ListToolbar", () => {
  it("invalidates open fiches after a scoped create, even without createPrefill", async () => {
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");
    render({ scope: { entityType: "project", id: "7" } });

    act(() => {
      container.querySelector<HTMLButtonElement>("[data-testid='create']")!.click();
    });
    await act(async () => {
      container.querySelector<HTMLButtonElement>("[data-testid='submit-create']")!.click();
      await Promise.resolve();
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.entityDetails() });
  });
});
