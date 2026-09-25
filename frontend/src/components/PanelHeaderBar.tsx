import type { ReactNode } from "react";
import { PanelToolbar } from "./PanelToolbar";
import type { PanelToolbarSlot } from "../mountOptions";

// The real JSF titlebar (focus.xhtml's `sideview-titlebar`, and panelContent.xhtml's overview
// equivalent) is ONE div holding both the title/header content and the toolbar form, side by
// side. PrimeReact's <Panel> instead splits those into two separate props (`header` and
// `icons`) — passing the toolbar as `icons` renders it correctly on screen, but it's the wrong
// piece of this component's own API to reach for: `icons` is generic "extra header buttons"
// (used for the built-in toggler too), not "the panel's toolbar", and it kept the toolbar out of
// what should be one single header node. This bar IS that single header node — title content on
// the left, the toolbar on the right — passed whole to <Panel header={...}> so every panel
// (Home/List/Detail) reproduces the real one-piece titlebar rather than reconstructing it out of
// two separate Panel slots.
export interface PanelHeaderBarProps {
  title: ReactNode;
  toolbar?: PanelToolbarSlot;
  // Prev/next, placed in the toolbar's navigation group.
  navigation?: ReactNode;
  // "main": title left, toolbar pushed to the right. "overview": toolbar first (its secondary
  // actions folded into a "…" menu), then the title — the narrow pane reads left to right.
  layout?: "main" | "overview";
}

export function PanelHeaderBar({ title, toolbar, navigation, layout = "main" }: PanelHeaderBarProps) {
  const overview = layout === "overview";
  // No toolbar (a pane rendered without one): prev/next still show, on their own.
  const toolbarNode = toolbar ? (
    <PanelToolbar
      chrome={toolbar.chrome}
      organizationId={toolbar.organizationId}
      actions={toolbar.actions}
      navigation={navigation}
      compact={overview}
    />
  ) : (
    navigation
  );
  return (
    <div
      className={`sideview-titlebar sideview-titlebar-${layout}`}
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: overview ? "flex-start" : "space-between",
        gap: "0.5em",
        width: "100%",
      }}
    >
      {overview && toolbarNode}
      <div style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap", minWidth: 0 }}>{title}</div>
      {!overview && toolbarNode}
    </div>
  );
}
