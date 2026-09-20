import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
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
});
