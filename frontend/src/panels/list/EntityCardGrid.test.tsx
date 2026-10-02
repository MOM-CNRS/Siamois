import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import type { EntityTypeConfig } from "../../entities/types";
import { EntityCardGrid, type EntityCardGridProps } from "./EntityCardGrid";

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const config = {
  key: "fake",
  icon: "bi bi-x",
  list: {
    columns: [{ key: "fullIdentifier", header: "Id", identifier: true, render: (r: { fullIdentifier: string }) => r.fullIdentifier }],
    card: { subtitle: () => "Projet A", details: () => [{ icon: "bi bi-bucket", label: "3 mobiliers" }, { icon: "bi bi-x", label: null }] },
  },
} as unknown as EntityTypeConfig<unknown, unknown>;

describe("EntityCardGrid", () => {
  let container: HTMLDivElement;
  let root: Root;
  beforeEach(() => {
    container = document.createElement("div");
    document.body.appendChild(container);
    root = createRoot(container);
  });
  afterEach(() => {
    act(() => root.unmount());
    container.remove();
  });

  function render(props: Partial<EntityCardGridProps> = {}) {
    act(() =>
      root.render(
        <EntityCardGrid
          entityType="fake"
          config={config}
          rows={[{ id: "1", fullIdentifier: "UE-1", type: { resolvedLabel: "Couche" }, validated: "VALIDATED" }]}
          isLoading={false}
          totalCount={1}
          params={{}}
          offset={0}
          limit={25}
          onPage={() => {}}
          renderRowActions={() => <span className="row-actions" />}
          {...props}
        />,
      ),
    );
  }

  it("shows one card per row with its type, subtitle, non-empty details and actions", () => {
    render();
    const cards = container.querySelectorAll(".entity-card");
    expect(cards).toHaveLength(1);
    expect(cards[0].textContent).toContain("UE-1");
    expect(cards[0].textContent).toContain("Couche");
    expect(cards[0].textContent).toContain("Projet A");
    expect(cards[0].querySelectorAll(".entity-card-details li")).toHaveLength(1);
    expect(cards[0].querySelector(".row-actions")).not.toBeNull();
  });

  it("opens the row in the overview with its label and status", () => {
    const onOpenOverview = vi.fn();
    render({ onOpenOverview });
    act(() => (container.querySelector(".sia-clickable-card") as HTMLElement).click());
    expect(onOpenOverview).toHaveBeenCalledWith("fake", "1", { label: "UE-1", validated: "VALIDATED" });
  });

  it("does not open the row when one of its actions is clicked", () => {
    const onOpenOverview = vi.fn();
    render({ onOpenOverview, renderRowActions: () => <button className="an-action">x</button> });
    act(() => (container.querySelector(".an-action") as HTMLElement).click());
    expect(onOpenOverview).not.toHaveBeenCalled();
  });

  it("says so when there is no row", () => {
    render({ rows: [], totalCount: 0 });
    expect(container.querySelector(".entity-card-empty")).not.toBeNull();
  });
});
