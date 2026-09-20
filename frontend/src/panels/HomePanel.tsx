import type { HomeWidgetDef } from "../entities/types";

// Widget-slot design (plan §3/§8 phase 2): HomePanel itself never knows about "recent projects"
// or "Project card" — those are widgets each entity's config.tsx contributes via its optional
// `home.widgets` factory (entities/types.ts), assembled in App.tsx via getAllEntityTypes()
// (phase 7). Adding a future map widget, or another entity's count card, is then a config-only
// change, not an edit to HomePanel.
export interface HomePanelProps {
  widgets: HomeWidgetDef[];
}

export function HomePanel({ widgets }: HomePanelProps) {
  if (widgets.length === 0) {
    return <div className="home-panel home-panel-empty" />;
  }

  return (
    <div className="home-panel">
      {widgets.map((widget) => (
        <div key={widget.key} className="home-panel-widget">
          {widget.render()}
        </div>
      ))}
    </div>
  );
}
