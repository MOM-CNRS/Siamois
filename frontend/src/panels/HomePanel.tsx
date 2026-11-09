import type {HomeWidgetDef} from "../entities/shared/types";
import {PanelHeaderBar} from "../components/layout/PanelHeaderBar";
import type {PanelToolbarSlot} from "../mountOptions";
import {t} from "../i18n";

// Widget-slot design (plan §3/§8 phase 2): HomePanel itself never knows about "recent projects"
// or "Project card" — those are widgets each entity's config.tsx contributes via its optional
// `home.widgets` factory (entities/shared/types.ts), assembled in App.tsx via getAllEntityTypes()
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

  // "panel"-kind widgets (the default) are self-contained <Panel>s (RecentProjectsWidget wraps itself)
  // and render standalone, one after another. "card"-kind widgets are a table's card for the data
  // access section (components/home/DataAccessSection), which places them itself: never rendered here.
  const panelWidgets = widgets.filter((w) => (w.kind ?? "panel") === "panel");

  return (
    <div className="home-panel">
      {/* homePanelHeader.xhtml (icon + title) plus the generic toolbar — this is focus.xhtml's
          own outer titlebar chrome (like EntityListPanel's/EntityDetailPanel's own header), not
          one of the two content panels below, so it's a plain row, not itself a <Panel>. */}
      <PanelHeaderBar title={t("home.title")} toolbar={toolbar} />

      <div className="home-panel-content">
        {panelWidgets.map((widget) => (
          <div key={widget.key} className="home-panel-widget">
            {widget.render()}
          </div>
        ))}
      </div>
    </div>
  );
}
