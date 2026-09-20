// Shape of the options object focus.xhtml's inline script passes to window.SiamoisMainPanel.mount
// (plan §7.2/§8 phase 8).
export type PanelKind = "home" | "list" | "detail";

// Bridged via p:remoteCommand from the main panel's own titlebar (focus.xhtml) — a key is present
// only when JSF's own `rendered` condition for that button allows it (e.g. `create` is omitted
// entirely when creationUnitKind is null or the session isn't in write mode), so PanelToolbar
// never has to re-derive permission logic client-side.
export interface PanelActions {
  duplicate?: () => void;
  refresh?: () => void;
  create?: () => void;
  settings?: () => void;
}

// The overview pane's own titlebar (panelContent.xhtml) has two extra buttons the main panel's
// titlebar doesn't: closing the overview, and popping it into full focus mode.
export interface OverviewActions extends PanelActions {
  closeOverview?: () => void;
  fullscreen?: () => void;
}

// Everything PanelToolbar needs to render one titlebar's bookmark button + resolve a display
// title, for either the main panel or the overview — mirrors panelModel.ressourceUri()/
// resolveTitleOrTitleCode()/isBookmarked().
export interface PanelChrome {
  resourceUri: string;
  title: string;
  bookmarked: boolean;
}

export interface MountOptions {
  panelKind: PanelKind;
  entityType: string;
  entityId?: string | number;
  overviewEntityType?: string;
  overviewEntityId?: string | number;
  organizationId?: number;
  overviewOrganizationId?: number;
  basePath: string;
  csrf: { headerName: string; token: string };
  main: PanelChrome;
  actions?: PanelActions;
  overview?: PanelChrome;
  overviewActions?: OverviewActions;
  onNavigate?: (entityType: string, id: string | number) => void;
}
