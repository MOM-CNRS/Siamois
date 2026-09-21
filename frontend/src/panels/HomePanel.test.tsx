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
});
