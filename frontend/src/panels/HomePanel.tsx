import type { HomeWidgetDef } from "../entities/types";
import { Panel } from "primereact/panel";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import type { PanelToolbarSlot } from "../mountOptions";

// Widget-slot design (plan §3/§8 phase 2): HomePanel itself never knows about "recent projects"
// or "Project card" — those are widgets each entity's config.tsx contributes via its optional
// `home.widgets` factory (entities/types.ts), assembled in App.tsx via getAllEntityTypes()
// (phase 7). Adding a future map widget, or another entity's count card, is then a config-only
// change, not an edit to HomePanel.
export interface HomePanelProps {
  widgets: HomeWidgetDef[];
  // Omitted once navigated away from the entity this toolbar was built for (App.tsx never
  // actually does this for Home — Home is only ever the initial mount — but the prop stays
  // optional for the same reason EntityListPanel's/EntityDetailPanel's are).
  toolbar?: PanelToolbarSlot;
}

export function HomePanel({ widgets, toolbar }: HomePanelProps) {
  if (widgets.length === 0) {
    return <div className="home-panel home-panel-empty" />;
  }

  // homePanel.xhtml is NOT one big panel — it's a title, then two separate sibling p:panels
  // ("Mes derniers projets" / myActionUnits, and "Accéder aux bases de données" / dbAccess).
  // "panel"-kind widgets (the default) are already self-contained <Panel>s (RecentProjectsWidget
  // wraps itself) and render standalone, one after another; "card"-kind widgets are tiles for
  // the one shared database-access panel/grid and never get a top-level panel of their own.
  const panelWidgets = widgets.filter((w) => (w.kind ?? "panel") === "panel");
  const cardWidgets = widgets
    .filter((w) => w.kind === "card")
    .sort((a, b) => (a.order ?? Number.MAX_SAFE_INTEGER) - (b.order ?? Number.MAX_SAFE_INTEGER));

  return (
    <div className="home-panel">
      {/* homePanelHeader.xhtml (icon + title) plus the generic toolbar — this is focus.xhtml's
          own outer titlebar chrome (like EntityListPanel's/EntityDetailPanel's own header), not
          one of the two content panels below, so it's a plain row, not itself a <Panel>. */}
      <PanelHeaderBar title={"Accueil"} toolbar={toolbar} />

      <div className="home-panel-content" style={{ padding: "1em", display: "flex", flexDirection: "column", gap: "1em" }}>
        {panelWidgets.map((widget) => (
          <div key={widget.key} className="home-panel-widget">
            {widget.render()}
          </div>
        ))}

        {cardWidgets.length > 0 && (
          <Panel header="Accéder aux bases de données" toggleable className="sia-form-panel">
            <div
              className="home-panel-database-grid"
              style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: "1rem" }}
            >
              {cardWidgets.map((widget) => (
                <div key={widget.key} className="home-panel-widget">
                  {widget.render()}
                </div>
              ))}
            </div>
          </Panel>
        )}
      </div>
    </div>
  );
}
