import type {HomeWidgetDef} from "../entities/types";
import {Panel} from "primereact/panel";
import {PanelHeaderBar} from "../components/PanelHeaderBar";
import type {PanelToolbarSlot} from "../mountOptions";

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

export function HomePanel({widgets, toolbar}: HomePanelProps) {
    if (widgets.length === 0) {
        return <div className="home-panel home-panel-empty"/>;
    }

    return (
        // homePanelHeader.xhtml (icon + title) plus the generic toolbar, as one single
        // PanelHeaderBar passed as this <Panel>'s `header` (plan §7/§8 follow-up: "toolbar is
        // part of the header", not PrimeReact's separate `icons` slot) — not a toolbar strip
        // rendered by the caller above this component.
        <Panel
            className="home-panel"
            header={<PanelHeaderBar title={"Accueil"} toolbar={toolbar} />}
        >
            {widgets.map((widget) => (
                <div key={widget.key} className="home-panel-widget">
                    {widget.render()}
                </div>
            ))}
        </Panel>
    );
}
