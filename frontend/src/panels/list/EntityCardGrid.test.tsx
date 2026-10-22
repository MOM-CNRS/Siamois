import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { EntityTypeConfig } from "../../entities/shared/types";
import { EntityCardGrid, type EntityCardGridProps } from "./EntityCardGrid";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

vi.mock("./useDocumentThumbnail", () => ({
  useDocumentThumbnail: (id: string | undefined) => (id === "7" ? "blob:thumb" : null),
}));

const config = {
  key: "recordingUnit",
  icon: "bi bi-layers",
  list: {
    columns: [{ key: "id", header: "Id", identifier: true, render: (r: { fullIdentifier: string }) => r.fullIdentifier }],
    card: {
      image: (r: { img?: string }) => (r.img ? { documentId: r.img } : null),
      subtitle: (r: { project?: string }) => r.project,
    },
  },
} as unknown as EntityTypeConfig<unknown, unknown>;

let root: Root | undefined;
let host: HTMLElement | undefined;

afterEach(() => {
  act(() => root?.unmount());
  host?.remove();
});

function renderGrid(overrides: Partial<EntityCardGridProps> = {}) {
  const onOpenOverview = vi.fn();
  const rows = [
    { id: "1", fullIdentifier: "UE-1", img: "7", project: "Chantier A" },
    { id: "2", fullIdentifier: "UE-2" },
  ];
  host = document.createElement("div");
  document.body.appendChild(host);
  root = createRoot(host);
  act(() =>
    root!.render(
      <QueryClientProvider client={new QueryClient()}>
        <EntityCardGrid
          entityType="recordingUnit"
          config={config}
          rows={rows}
          isLoading={false}
          totalCount={2}
          params={{}}
          offset={0}
          limit={25}
          onPage={() => {}}
          renderRowActions={(r) => <button className="row-action">{`actions-${String(r.id)}`}</button>}
          onOpenOverview={onOpenOverview}
          selectedRows={[]}
          onSelectionChange={() => {}}
          {...overrides}
        />
      </QueryClientProvider>,
    ),
  );
  return { onOpenOverview, host };
}

describe("EntityCardGrid", () => {
  it("shows the image when there is one and the type icon otherwise", () => {
    const { host } = renderGrid();
    expect(host.querySelectorAll("img")).toHaveLength(1);
    expect(host.querySelectorAll(".entity-card-placeholder")).toHaveLength(1);
  });

  it("puts the chip and the row actions in the footer", () => {
    const { host } = renderGrid();
    const footer = host.querySelector(".entity-card-footer")!;
    expect(footer.textContent).toContain("UE-1");
    expect(footer.querySelector(".row-action")?.textContent).toBe("actions-1");
  });

  it("opens the overview from the card, but not from the actions", () => {
    const { onOpenOverview, host } = renderGrid();
    act(() => host.querySelectorAll<HTMLElement>(".row-action")[1].click());
    expect(onOpenOverview).not.toHaveBeenCalled();
    act(() => host.querySelectorAll<HTMLElement>(".entity-card")[0].click());
    expect(onOpenOverview).toHaveBeenCalledTimes(1);
    expect(onOpenOverview.mock.calls[0][1]).toBe("1");
  });

  it("opens the overview once when the chip is clicked", () => {
    const { onOpenOverview, host } = renderGrid();
    act(() => host.querySelectorAll<HTMLElement>(".entity-card-chip")[1].click());
    expect(onOpenOverview).toHaveBeenCalledTimes(1);
    expect(onOpenOverview.mock.calls[0][1]).toBe("2");
  });

  it("selects a card from its checkbox without opening it", () => {
    const onSelectionChange = vi.fn();
    const { host, onOpenOverview } = renderGrid({ onSelectionChange });
    act(() => host.querySelectorAll<HTMLElement>(".entity-card-select input")[1].click());
    expect(onSelectionChange).toHaveBeenCalledWith([expect.objectContaining({ id: "2" })]);
    expect(onOpenOverview).not.toHaveBeenCalled();
  });

  it("shows a checked box for a selected row", () => {
    const { host } = renderGrid({ selectedRows: [{ id: "1" }] });
    expect(host.querySelectorAll(".entity-card-select .p-checkbox.p-highlight")).toHaveLength(1);
  });

  it("puts the selection box in the picture, not in the footer", () => {
    const { host } = renderGrid();
    expect(host.querySelectorAll(".entity-card-media .entity-card-select")).toHaveLength(2);
    expect(host.querySelectorAll(".entity-card-footer .entity-card-select")).toHaveLength(0);
  });

  it("offers the whole image only for a card that has one", () => {
    const { host } = renderGrid();
    expect(host.querySelectorAll(".entity-card-zoom")).toHaveLength(1);
  });

  it("marks the card of the entity open in the overview as selected", () => {
    const { host } = renderGrid({ overviewEntityId: "2" });
    const selected = host.querySelectorAll(".entity-card.is-selected");
    expect(selected).toHaveLength(1);
    expect(selected[0].getAttribute("aria-current")).toBe("true");
  });
});
