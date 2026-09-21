import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { HomePanel } from "./HomePanel";

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
          toolbar={{ chrome: { resourceUri: "/welcome", title: "Accueil", bookmarked: false }, actions: { refresh: () => {} } }}
        />
      </QueryClientProvider>,
    );
    expect(html).toContain("bi-arrow-clockwise");
  });

  // homePanel.xhtml is two separate sibling p:panels ("Mes derniers projets"/myActionUnits and
  // "Accéder aux bases de données"/dbAccess), not one panel holding everything.
  it("renders a 'panel'-kind widget standalone, not grouped with the database-access panel", () => {
    const html = renderToStaticMarkup(
      <HomePanel widgets={[{ key: "recent", render: () => <span>Recent projects panel</span> }]} />,
    );
    expect(html).toContain("Recent projects panel");
    expect(html).not.toContain("Accéder aux bases de données");
  });

  it("groups every 'card'-kind widget into the shared 'Accéder aux bases de données' panel", () => {
    const html = renderToStaticMarkup(
      <HomePanel
        widgets={[
          { key: "card-a", kind: "card", render: () => <span>Card A</span> },
          { key: "card-b", kind: "card", render: () => <span>Card B</span> },
        ]}
      />,
    );
    expect(html).toContain("Accéder aux bases de données");
    expect(html).toContain("Card A");
    expect(html).toContain("Card B");
  });
});
