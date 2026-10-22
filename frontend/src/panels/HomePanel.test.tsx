import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { renderToStaticMarkup } from "react-dom/server";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { HomePanel } from "./HomePanel";
import { PaneStateProvider, PaneStateStore } from "./pane/paneState";

describe("HomePanel", () => {
  it("renders an empty shell when there are no widgets", () => {
    const html = renderToStaticMarkup(<HomePanel widgets={[]} />);
    expect(html).toContain("home-panel-empty");
  });

  it("renders each registered widget in order", () => {
    const html = renderToStaticMarkup(
      <HomePanel
        widgets={[
          { key: "a", render: () => <span>Widget A</span> },
          { key: "b", render: () => <span>Widget B</span> },
        ]}
      />,
    );
    expect(html.indexOf("Widget A")).toBeLessThan(html.indexOf("Widget B"));
  });

  it("renders the toolbar in its own header, not as a separate strip (plan §7/§8 follow-up)", () => {
    const queryClient = new QueryClient();
    const html = renderToStaticMarkup(
      <QueryClientProvider client={queryClient}>
        <HomePanel
          widgets={[{ key: "a", render: () => <span>Widget A</span> }]}
          toolbar={{ chrome: { resourceUri: "/welcome", title: "Accueil", bookmarked: false }, actions: { duplicate: () => {} } }}
        />
      </QueryClientProvider>,
    );
    expect(html).toContain("bi-copy");
  });

  // homePanel.xhtml is two separate sibling p:panels ("Mes derniers projets"/myActionUnits and
  // "Accès par table"/dbAccess), not one panel holding everything.
  it("renders a 'panel'-kind widget standalone, not grouped with the database-access panel", () => {
    const html = renderToStaticMarkup(
      <HomePanel widgets={[{ key: "recent", render: () => <span>Recent projects panel</span> }]} />,
    );
    expect(html).toContain("Recent projects panel");
    expect(html).not.toContain("Accès par table");
  });

  it("groups every 'card'-kind widget into the shared 'Accès par table' panel", () => {
    const html = renderToStaticMarkup(
      <HomePanel
        widgets={[
          { key: "card-a", kind: "card", render: () => <span>Card A</span> },
          { key: "card-b", kind: "card", render: () => <span>Card B</span> },
        ]}
      />,
    );
    expect(html).toContain("Accès par table");
    expect(html).toContain("Card A");
    expect(html).toContain("Card B");
  });

  // Widgets arrive in mount.ts's registration order; cards follow homePanel.xhtml's order instead.
  it("sorts 'card'-kind widgets by `order`, cards without one last", () => {
    const html = renderToStaticMarkup(
      <HomePanel
        widgets={[
          { key: "none", kind: "card", render: () => <span>Card none</span> },
          { key: "c", kind: "card", order: 30, render: () => <span>Card 30</span> },
          { key: "a", kind: "card", order: 10, render: () => <span>Card 10</span> },
        ]}
      />,
    );
    expect(html.indexOf("Card 10")).toBeLessThan(html.indexOf("Card 30"));
    expect(html.indexOf("Card 30")).toBeLessThan(html.indexOf("Card none"));
  });
});

describe("HomePanel folded state", () => {
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

  it("keeps the database panel folded when the home panel mounts again", () => {
    const store = new PaneStateStore();
    const renderWithStore = () =>
      act(() => {
        root.render(
          <PaneStateProvider store={store}>
            <HomePanel widgets={[{ key: "card", kind: "card", render: () => <span>Card</span> }]} />
          </PaneStateProvider>,
        );
      });
    const isFolded = () => container.querySelector(".p-panel-toggler")?.getAttribute("aria-expanded") === "false";

    renderWithStore();
    expect(isFolded()).toBe(false);
    act(() => {
      (container.querySelector(".p-panel-toggler") as HTMLElement).click();
    });
    expect(isFolded()).toBe(true);

    act(() => root.render(null));
    renderWithStore();
    expect(isFolded()).toBe(true);
  });
});
